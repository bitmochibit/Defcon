package me.mochibit.defcon.foundation.util

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet
import kotlinx.coroutines.*
import me.mochibit.defcon.foundation.services.eventService
import me.mochibit.defcon.foundation.warn
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private data class BlockChange(
    val pos: BlockPos,
    val newState: BlockState,
    val updateBlock: Boolean,
)

class BlockChanger private constructor(
    private val level: Level,
) {
    private val pendingByChunk = ConcurrentHashMap<Long, ConcurrentLinkedQueue<BlockChange>>()
    private val pendingCount = AtomicLong(0)

    private val pendingCountByChunk = ConcurrentHashMap<Long, AtomicInteger>()
    private val chunkCompletionListeners = ConcurrentHashMap<Long, ConcurrentLinkedQueue<() -> Unit>>()
    private val completionLock = Any()

    var blocksPerTick: Int = 5000
        set(value) { field = value.coerceAtLeast(1) }

    private val changedPositionsThisSession = LongOpenHashSet()
    private val dirtyChunksThisSession = LongOpenHashSet()

    fun addBlockChange(
        x: Int, y: Int, z: Int,
        newState: BlockState,
        updateBlock: Boolean = false,
    ) {
        val key = ChunkPos.asLong(x shr 4, z shr 4)
        pendingByChunk.computeIfAbsent(key) { ConcurrentLinkedQueue() }
            .add(BlockChange(BlockPos(x, y, z), newState, updateBlock))
        pendingCount.incrementAndGet()
        pendingCountByChunk.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
    }

    fun onChunkFullyProcessed(chunkX: Int, chunkZ: Int, listener: () -> Unit) {
        val key = ChunkPos.asLong(chunkX, chunkZ)
        synchronized(completionLock) {
            val counter = pendingCountByChunk[key]
            if (counter == null || counter.get() <= 0) {
                listener()
                return
            }
            chunkCompletionListeners.computeIfAbsent(key) { ConcurrentLinkedQueue() }.add(listener)
        }
    }

    private fun onChunkItemProcessed(chunkKey: Long) {
        val counter = pendingCountByChunk[chunkKey] ?: return
        if (counter.decrementAndGet() > 0) return

        val listenersToFire = synchronized(completionLock) {
            val current = pendingCountByChunk[chunkKey]
            if (current == null || current.get() > 0) return@synchronized null
            pendingCountByChunk.remove(chunkKey)
            chunkCompletionListeners.remove(chunkKey)
        }
        listenersToFire?.forEach { it() }
    }

    private fun pumpPendingWork() {
        if (pendingByChunk.isEmpty()) return
        val serverLevel = level as? ServerLevel ?: return

        var applied = 0
        val iterator = pendingByChunk.entries.iterator()

        while (applied < blocksPerTick && iterator.hasNext()) {
            val (chunkKey, queue) = iterator.next()
            if (queue.isEmpty()) { iterator.remove(); continue }

            val chunkX = ChunkPos.getX(chunkKey)
            val chunkZ = ChunkPos.getZ(chunkKey)

            val chunk = serverLevel.chunkSource.getChunkNow(chunkX, chunkZ) ?: continue

            val budget = blocksPerTick - applied
            val dirtyBySection = HashMap<Int, ShortOpenHashSet>()
            val deferredUpdates = ArrayList<BlockChange>()
            val mutablePos = BlockPos.MutableBlockPos()
            var chunkChanged = false
            var takenFromThisChunk = 0

            while (takenFromThisChunk < budget) {
                val change = queue.poll() ?: break
                pendingCount.decrementAndGet()
                takenFromThisChunk++

                if (change.updateBlock) {
                    deferredUpdates.add(change)
                    continue
                }

                try {
                    mutablePos.set(change.pos)
                    val oldState = chunk.getBlockState(mutablePos)
                    if (oldState == change.newState) continue

                    chunk.setBlockState(mutablePos, change.newState, true) ?: continue

                    val sectionIndex = chunk.getSectionIndex(change.pos.y)
                    if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) continue

                    val localX = change.pos.x and 15
                    val localY = change.pos.y and 15
                    val localZ = change.pos.z and 15
                    val packedLocal = ((localX shl 8) or (localZ shl 4) or localY).toShort()
                    dirtyBySection.getOrPut(sectionIndex) { ShortOpenHashSet() }.add(packedLocal)

                    if (oldState.block !== change.newState.block) {
                        changedPositionsThisSession.add(change.pos.asLong())
                    }
                    chunkChanged = true
                } catch (e: Exception) {
                    "BlockChanger: block write failed at ${change.pos}: ${e}".warn()
                } finally {
                    onChunkItemProcessed(chunkKey)
                }
            }

            if (dirtyBySection.isNotEmpty()) {
                val trackingPlayers = serverLevel.chunkSource.chunkMap.getPlayers(ChunkPos(chunkX, chunkZ), false)
                if (trackingPlayers.isNotEmpty()) {
                    for ((sectionIndex, positions) in dirtyBySection) {
                        val sectionY = chunk.minSection + sectionIndex
                        val sectionPos = SectionPos.of(chunkX, sectionY, chunkZ)
                        val section = chunk.sections[sectionIndex]
                        val packet = ClientboundSectionBlocksUpdatePacket(sectionPos, positions, section)
                        trackingPlayers.forEach { it.connection.send(packet) }
                    }
                }
            }

            for (change in deferredUpdates) {
                try {
                    val oldState = level.getBlockState(change.pos)
                    if (oldState == change.newState) continue
                    level.setBlock(change.pos, change.newState, 3)
                    if (oldState.block !== change.newState.block) {
                        changedPositionsThisSession.add(change.pos.asLong())
                    }
                    chunkChanged = true
                } catch (e: Exception) {
                    "BlockChanger: setBlock failed at ${change.pos}: ${e}".warn()
                } finally {
                    onChunkItemProcessed(chunkKey)
                }
            }

            if (chunkChanged) {
                chunk.isUnsaved = true
                dirtyChunksThisSession.add(chunkKey)
            }

            applied += takenFromThisChunk
            if (queue.isEmpty()) iterator.remove()
        }
    }

    private fun hasOutstandingWork(): Boolean = pendingCount.get() > 0

    suspend fun flush() {
        while (hasOutstandingWork()) {
            delay(20.milliseconds)
        }
        delay(50.milliseconds)
    }

    companion object {
        private val instances = ConcurrentHashMap<ResourceKey<Level>, BlockChanger>()
        private var listenerRegistered = false

        @JvmStatic
        fun getInstance(level: Level): BlockChanger {
            ensureTickListenerRegistered()
            return instances.computeIfAbsent(level.dimension()) { BlockChanger(level) }
        }

        private fun ensureTickListenerRegistered() {
            if (listenerRegistered) return
            listenerRegistered = true
            eventService.onLevelTick { tickedLevel: LevelAccessor ->
                if (tickedLevel !is ServerLevel) return@onLevelTick
                val key = (tickedLevel as? Level)?.dimension() ?: return@onLevelTick
                instances[key]?.pumpPendingWork()
            }
        }

        @JvmStatic
        fun shutdownAll() {
            instances.clear()
        }
    }
}