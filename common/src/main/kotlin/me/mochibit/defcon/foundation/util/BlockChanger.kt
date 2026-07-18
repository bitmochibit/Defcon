package me.mochibit.defcon.foundation.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.async.withMainContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private data class BlockChange(
    val pos: BlockPos,
    val newState: BlockState,
    val updateBlock: Boolean,
)

private val TRACKED_HEIGHTMAPS = arrayOf(
    Heightmap.Types.MOTION_BLOCKING,
    Heightmap.Types.WORLD_SURFACE,
)

class BlockChanger private constructor(
    private val level: Level,
) {

    private val workerScope =
        CoroutineScope(ServerCoroutineScope.coroutineContext + SupervisorJob(ServerCoroutineScope.coroutineContext[Job]))

    private val workerCount = 1
    private val batchSize = 10000
    private val batchTimeoutMs = 50L

    private val processingActive = AtomicBoolean(false)
    private val pendingChanges = AtomicLong(0)

    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()

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

        repeat(workerCount) {
            val job =
                workerScope.launch(Dispatchers.Default) {
                    val batch = ArrayList<BlockChange>(batchSize)

                    while (processingActive.get()) {
                        var collected = 0
                        val startTime = System.currentTimeMillis()

                        while (collected < batchSize &&
                            (System.currentTimeMillis() - startTime) < batchTimeoutMs
                        ) {
                            val change = blockChannel.tryReceive().getOrNull() ?: break
                            batch.add(change)
                            pendingChanges.decrementAndGet()
                            collected++
                        }

                        if (batch.isNotEmpty()) {
                            applyBatchOptimized(batch.toList())
                            batch.clear()
                        } else {
                            delay(5.milliseconds)
                        }
                    }

                    if (batch.isNotEmpty()) {
                        applyBatchOptimized(batch.toList())
                    }
                }
            processingJobs.add(job)
        }
    }


    private suspend fun applyBatchOptimized(batch: List<BlockChange>) {
        val blocksPerTick = 5000

        for (chunkBatch in batch.chunked(blocksPerTick)) {
            withMainContext {
                val changedPositions = ArrayList<BlockPos>(chunkBatch.size)
                for (change in chunkBatch) {
                    try {
                        val pos = change.pos

                        val chunk = level.getChunkAt(pos)

                        val sectionIndex = chunk.getSectionIndex(pos.y)
                        if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) continue
                        val section = chunk.sections[sectionIndex]

                        val localX = pos.x and 15
                        val localY = pos.y and 15
                        val localZ = pos.z and 15

                        val oldState = section.getBlockState(localX, localY, localZ)
                        if (oldState == change.newState) continue

                        section.setBlockState(localX, localY, localZ, change.newState, false)
                        chunk.isUnsaved = true

                        for (type in TRACKED_HEIGHTMAPS) {
                            chunk.getOrCreateHeightmapUnprimed(type)
                                .update(localX, pos.y, localZ, change.newState)
                        }

                        level.chunkSource.lightEngine.checkBlock(pos)
                        level.sendBlockUpdated(pos, oldState, change.newState, 2)

                        if (oldState.block !== change.newState.block) {
                            changedPositions.add(pos)
                        }
                    } catch (e: Exception) {
                        println("Failed to write block at ${change.pos}: ${e.message}")
                    }
                }

                if (changedPositions.isNotEmpty()) {
                    cleanupUnsupported(changedPositions)
                }
            }
            delay(1.milliseconds)
        }
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

            val survives = try { state.canSurvive(level, pos) } catch (_: Exception) { true }
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
                for (type in TRACKED_HEIGHTMAPS) {
                    chunk.getOrCreateHeightmapUnprimed(type).update(localX, pos.y, localZ, Blocks.AIR.defaultBlockState())
                }
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