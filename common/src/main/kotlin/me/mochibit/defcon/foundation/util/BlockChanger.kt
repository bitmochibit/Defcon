package me.mochibit.defcon.foundation.util

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.extension.awaitUnpaused
import me.mochibit.defcon.foundation.services.eventService
import me.mochibit.defcon.foundation.warn
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ThreadedLevelLightEngine
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
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

    private val collectBatchSize = 30000
    private val collectTimeoutMs = 50L

    var blocksPerTick: Int = 5
        set(value) { field = value.coerceAtLeast(1) }

    private val processingActive = AtomicBoolean(false)
    private val pendingChanges = AtomicLong(0)

    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()

    private val readyBatches = ConcurrentLinkedDeque<Long2ObjectOpenHashMap<MutableList<BlockChange>>>()

    private val changedPositionsThisSession = LongOpenHashSet()
    private val dirtyChunksThisSession = LongOpenHashSet()

    init {
        initializeChannel()
        startCollecting()
    }

    private fun initializeChannel() {
        blockChannel = Channel(capacity = Channel.UNLIMITED)
    }


    private fun startCollecting() {
        if (!processingActive.compareAndSet(false, true)) return
        processingJobs.clear()

        val job = workerScope.launch(Dispatchers.Default) {
            val batch = ArrayList<BlockChange>(collectBatchSize)
            try {
                while (processingActive.get()) {
                    if (level is ServerLevel) level.awaitUnpaused()

                    var collected = 0
                    val startTime = System.currentTimeMillis()
                    while (collected < collectBatchSize && (System.currentTimeMillis() - startTime) < collectTimeoutMs) {
                        val change = blockChannel.tryReceive().getOrNull() ?: break
                        batch.add(change)
                        pendingChanges.decrementAndGet()
                        collected++
                    }

                    if (batch.isNotEmpty()) {
                        readyBatches.add(groupByChunk(batch))
                        batch.clear()
                    } else {
                        delay(5.milliseconds)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "BlockChanger collector died unexpectedly, will self-heal on next change ${e}".warn()
            } finally {
                processingActive.set(false)
            }
        }
        processingJobs.add(job)
    }

    private fun pumpPendingWork() {
        var appliedThisTick = 0
        while (appliedThisTick < blocksPerTick) {
            val nextBatch = readyBatches.peek() ?: break


            val remainingBudget = blocksPerTick - appliedThisTick
            val (toApply, leftover) = splitByBudget(nextBatch, remainingBudget)

            if (toApply.isNotEmpty()) {
                appliedThisTick += toApply.values.sumOf { it.size }
                applyGroupedBatch(toApply)
            }

            if (leftover.isEmpty()) {
                readyBatches.poll()
            } else {
                readyBatches.poll()
                readyBatches.offerFirst(leftover)
            }

            if (appliedThisTick >= blocksPerTick) break
        }
    }

    private fun ConcurrentLinkedQueue<Long2ObjectOpenHashMap<MutableList<BlockChange>>>.addFirstCompat(
        item: Long2ObjectOpenHashMap<MutableList<BlockChange>>,
    ) {
        val tempList = ArrayList<Long2ObjectOpenHashMap<MutableList<BlockChange>>>(this.size + 1)
        tempList.add(item)
        var polled = this.poll()
        while (polled != null) {
            tempList.add(polled)
            polled = this.poll()
        }
        tempList.forEach { this.add(it) }
    }

    private fun splitByBudget(
        source: Long2ObjectOpenHashMap<MutableList<BlockChange>>,
        budget: Int,
    ): Pair<Long2ObjectOpenHashMap<MutableList<BlockChange>>, Long2ObjectOpenHashMap<MutableList<BlockChange>>> {
        val totalSize = source.values.sumOf { it.size }
        if (totalSize <= budget) {
            return source to Long2ObjectOpenHashMap()
        }

        val taken = Long2ObjectOpenHashMap<MutableList<BlockChange>>()
        val remaining = Long2ObjectOpenHashMap<MutableList<BlockChange>>()
        var used = 0

        for ((chunkKey, changes) in source) {
            if (used >= budget) {
                remaining[chunkKey] = changes
                continue
            }
            val space = budget - used
            if (changes.size <= space) {
                taken[chunkKey] = changes
                used += changes.size
            } else {
                taken[chunkKey] = changes.subList(0, space).toMutableList()
                remaining[chunkKey] = changes.subList(space, changes.size).toMutableList()
                used += space
            }
        }
        return taken to remaining
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
                val mutablePos = BlockPos.MutableBlockPos()

                for (change in changes) {
                    try {
                        val pos = change.pos

                        if (change.updateBlock) {
                            deferredUpdates.add(change)
                            continue
                        }

                        mutablePos.set(pos)
                        val oldState = chunk.getBlockState(mutablePos)
                        if (oldState == change.newState) continue

                        val resultState = chunk.setBlockState(mutablePos, change.newState, true)
                        if (resultState == null) continue

                        val sectionIndex = chunk.getSectionIndex(pos.y)
                        if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) continue

                        val localX = pos.x and 15
                        val localY = pos.y and 15
                        val localZ = pos.z and 15
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
//                    lightEngine?.lightChunk(chunk, false)
                }
            } catch (e: Exception) {
                failures += changes.size
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

        val lightEngine = level.chunkSource.lightEngine as? ThreadedLevelLightEngine
        val dirtyChunksThisCall = LongOpenHashSet()

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
                val resultState = chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), true)
                if (resultState != null) {
                    chunk.isUnsaved = true
                    changedPositionsThisSession.add(pos.asLong())
                    dirtyChunksThisCall.add(ChunkPos.asLong(pos.x shr 4, pos.z shr 4))

                    val trackingPlayers = (level as ServerLevel).chunkSource.chunkMap.getPlayers(chunk.pos, false)
                    if (trackingPlayers.isNotEmpty()) {
                        val packet = ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState())
                        trackingPlayers.forEach { it.connection.send(packet) }
                    }
                }
            } catch (_: Exception) {
                continue
            }

            for (dir in Direction.entries) {
                val n = pos.relative(dir)
                if (visited.add(n.asLong())) queue.add(n)
            }
        }

//        if (lightEngine != null && dirtyChunksThisCall.isNotEmpty()) {
//            for (chunkKey in dirtyChunksThisCall) {
//                val chunkX = ChunkPos.getX(chunkKey)
//                val chunkZ = ChunkPos.getZ(chunkKey)
//                lightEngine.lightChunk(level.getChunk(chunkX, chunkZ), false)
//            }
//        }
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
            startCollecting()
        }
    }

    private fun hasOutstandingWork(): Boolean =
        pendingChanges.get() > 0 || readyBatches.isNotEmpty()

    suspend fun flush() {
        while (hasOutstandingWork()) {
            delay(20.milliseconds)
        }
        delay(50.milliseconds)
    }

    suspend fun cleanupUnsupportedAroundChanges() {
        val done = CompletableDeferred<Unit>()
        pendingCleanupTasks.add {
            cleanupUnsupported(changedPositionsThisSession.map { BlockPos.of(it) })
            changedPositionsThisSession.clear()
            done.complete(Unit)
        }
        done.await()
    }

    private val pendingCleanupTasks = ConcurrentLinkedQueue<() -> Unit>()


    private fun runPendingCleanupTasks() {
        var task = pendingCleanupTasks.poll()
        while (task != null) {
            task()
            task = pendingCleanupTasks.poll()
        }
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
                val key = (tickedLevel as? Level)?.dimension() ?: return@onLevelTick
                instances[key]?.let { changer ->
                    changer.pumpPendingWork()
                    changer.runPendingCleanupTasks()
                }
            }
        }

        @JvmStatic
        fun shutdownAll() {
            instances.values.forEach { it.shutdown() }
            instances.clear()
        }
    }
}