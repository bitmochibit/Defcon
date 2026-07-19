package me.mochibit.defcon.foundation.util

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.async.withMainContext
import me.mochibit.defcon.foundation.extension.awaitUnpaused
import me.mochibit.defcon.foundation.warn
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ThreadedLevelLightEngine
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
    private val workerScope =
        CoroutineScope(ServerCoroutineScope.coroutineContext + SupervisorJob(ServerCoroutineScope.coroutineContext[Job]))

    private val batchSize = 30000
    private val batchTimeoutMs = 50L

    private val processingActive = AtomicBoolean(false)
    private val pendingChanges = AtomicLong(0)

    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()

    private val changedPositionsThisSession = LongOpenHashSet()
    private val dirtyChunksThisSession = LongOpenHashSet()

    init {
        initializeChannel()
        startProcessing()
    }

    private fun initializeChannel() {
        blockChannel = Channel(capacity = Channel.UNLIMITED)
    }

    private fun startProcessing() {
        if (!processingActive.compareAndSet(false, true)) return
        processingJobs.clear()

        val job = workerScope.launch(Dispatchers.Default) {
            val batch = ArrayList<BlockChange>(batchSize)
            try {
                while (processingActive.get()) {
                    if (level is ServerLevel) level.awaitUnpaused()

                    var collected = 0
                    val startTime = System.currentTimeMillis()
                    while (collected < batchSize && (System.currentTimeMillis() - startTime) < batchTimeoutMs) {
                        val change = blockChannel.tryReceive().getOrNull() ?: break
                        batch.add(change)
                        pendingChanges.decrementAndGet()
                        collected++
                    }

                    if (batch.isNotEmpty()) {
                        val byChunk = groupByChunk(batch)
                        withMainContext {
                            applyGroupedBatch(byChunk)
                        }
                        batch.clear()
                    } else {
                        delay(5.milliseconds)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "BlockChanger worker died unexpectedly, will self-heal on next change ${e}".warn()
            } finally {
                processingActive.set(false)
            }
        }
        processingJobs.add(job)
    }

    private fun groupByChunk(batch: List<BlockChange>): Long2ObjectOpenHashMap<MutableList<BlockChange>> {
        val byChunk = Long2ObjectOpenHashMap<MutableList<BlockChange>>()
        for (change in batch) {
            val key = ChunkPos.asLong(change.pos.x shr 4, change.pos.z shr 4)
            byChunk.getOrPut(key) { mutableListOf() }.add(change)
        }
        return byChunk
    }

    private fun applyGroupedBatch(byChunk: Long2ObjectOpenHashMap<MutableList<BlockChange>>) {
        var failures = 0
        val lightEngine = level.chunkSource.lightEngine as? ThreadedLevelLightEngine
        for ((chunkKey, changes) in byChunk) {
            try {
                val chunkX = ChunkPos.getX(chunkKey)
                val chunkZ = ChunkPos.getZ(chunkKey)
                val chunk = level.getChunk(chunkX, chunkZ)
                var chunkChanged = false

                val dirtyBySection = HashMap<Int, ShortOpenHashSet>()
                val deferredUpdates = ArrayList<BlockChange>()

                for (change in changes) {
                    try {
                        val pos = change.pos
                        val sectionIndex = chunk.getSectionIndex(pos.y)
                        if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) continue
                        val section = chunk.sections[sectionIndex]

                        val localX = pos.x and 15
                        val localY = pos.y and 15
                        val localZ = pos.z and 15

                        val oldState = section.getBlockState(localX, localY, localZ)
                        if (oldState == change.newState) continue

                        if (change.updateBlock) {

                            deferredUpdates.add(change)
                            continue
                        }

                        section.setBlockState(localX, localY, localZ, change.newState, false)

                        val packedLocal = ((localX shl 8) or (localZ shl 4) or localY).toShort()
                        dirtyBySection.getOrPut(sectionIndex) { ShortOpenHashSet() }.add(packedLocal)

                        if (oldState.block !== change.newState.block) {
                            changedPositionsThisSession.add(pos.asLong())
                        }
                        chunkChanged = true
                    } catch (e: Exception) {
                        failures++
                        "BlockChanger: block write failed at ${change.pos}: ${e}".warn()
                    }
                }

                if (dirtyBySection.isNotEmpty()) {
                    val trackingPlayers = (level as ServerLevel).chunkSource.chunkMap.getPlayers(ChunkPos(chunkX, chunkZ), false)
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
                        failures++
                        "BlockChanger: setBlock failed at ${change.pos}: ${e}".warn()
                    }
                }

                if (chunkChanged) {
                    chunk.isUnsaved = true
                    dirtyChunksThisSession.add(chunkKey)
                    lightEngine?.lightChunk(chunk, false)
                }
            } catch (e: Exception) {
                failures += changes.size
                "BlockChanger: intero chunk $chunkKey fallito: ${e}".warn()
            }
        }
        if (failures > 0) "BlockChanger: $failures block writes failed in this batch".warn()
    }

    private fun cleanupUnsupported(seeds: Collection<BlockPos>) {
        val visited = HashSet<Long>()
        val queue = ArrayDeque<BlockPos>()
        for (p in seeds) {
            for (dir in Direction.entries) {
                val n = p.relative(dir)
                if (visited.add(n.asLong())) queue.add(n)
            }
        }

        var guard = 0
        while (queue.isNotEmpty() && guard++ < 20_000) {
            val pos = queue.removeFirst()
            val state = level.getBlockState(pos)
            if (state.isAir || !state.fluidState.isEmpty) continue

            val survives = try {
                state.canSurvive(level, pos)
            } catch (_: Exception) {
                true
            }
            if (survives) continue

            try {
                val chunk = level.getChunkAt(pos)
                val sectionIndex = chunk.getSectionIndex(pos.y)
                if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) continue
                val section = chunk.sections[sectionIndex]

                val localX = pos.x and 15
                val localY = pos.y and 15
                val localZ = pos.z and 15

                section.setBlockState(localX, localY, localZ, Blocks.AIR.defaultBlockState(), false)
                chunk.isUnsaved = true
                level.chunkSource.lightEngine.checkBlock(pos)
                level.sendBlockUpdated(pos, state, Blocks.AIR.defaultBlockState(), 2)
            } catch (_: Exception) {
                continue
            }

            for (dir in Direction.entries) {
                val n = pos.relative(dir)
                if (visited.add(n.asLong())) queue.add(n)
            }
        }
    }

    fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newState: BlockState,
        updateBlock: Boolean = false,
    ) {
        val change = BlockChange(BlockPos(x, y, z), newState, updateBlock)
        pendingChanges.incrementAndGet()
        blockChannel.trySend(change)

        if (!processingActive.get()) {
            startProcessing()
        }
    }

    suspend fun flush() {
        while (pendingChanges.get() > 0) {
            delay(20.milliseconds)
        }
        delay(50.milliseconds)
    }

    fun shutdown() {
        processingActive.set(false)
        workerScope.launch(Dispatchers.IO) {
            processingJobs.joinAll()
            processingJobs.clear()
            blockChannel.close()
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<ResourceKey<Level>, BlockChanger>()

        @JvmStatic
        fun getInstance(level: Level): BlockChanger {
            return instances.computeIfAbsent(level.dimension()) { BlockChanger(level) }
        }

        @JvmStatic
        fun shutdownAll() {
            instances.values.forEach { it.shutdown() }
            instances.clear()
        }
    }
}