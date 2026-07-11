package me.mochibit.defcon.foundation.util

import it.unimi.dsi.fastutil.shorts.ShortArraySet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.async.withMainContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
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
    private val level: ServerLevel,
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
        blockChannel = Channel(capacity = 100_000)
    }

    private fun startProcessing() {
        if (!processingActive.compareAndSet(false, true)) return

        processingJobs.clear()

        repeat(workerCount) {
            val job =
                workerScope.launch(Dispatchers.Default) {
                    val batch = ArrayList<BlockChange>(batchSize)

                    while (processingActive.get() || !blockChannel.isEmpty) {
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
        val blocksPerTick = 2000

        for (chunkBatch in batch.chunked(blocksPerTick)) {
            withMainContext {
                val sectionUpdates = HashMap<SectionPos, Pair<ShortArraySet, LevelChunkSection>>()

                // pos to notify -> pos that caused the change (i.e. the neighbor we DID change)
                val notifySet = HashMap<Long, Pair<BlockPos, BlockState>>()
                val changedKeys = HashSet<Long>()

                for (change in chunkBatch) {
                    changedKeys.add(change.pos.asLong())
                }

                for (change in chunkBatch) {
                    try {
                        val pos = change.pos
                        val chunk = level.getChunk(pos.x shr 4, pos.z shr 4) ?: continue

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

                        val secPos = SectionPos.of(pos)
                        val (localPositions, _) = sectionUpdates.getOrPut(secPos) {
                            Pair(ShortArraySet(), section)
                        }
                        localPositions.add(SectionPos.sectionRelativePos(pos))


                        if (oldState.block !== change.newState.block) {
                            for (dir in Direction.entries) {
                                val n = pos.relative(dir)
                                val key = n.asLong()
                                if (key !in changedKeys) {
                                    notifySet.putIfAbsent(key, pos to change.newState)
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                for ((secPos, data) in sectionUpdates) {
                    try {
                        val localPositions = data.first
                        val section = data.second
                        if (localPositions.isEmpty()) continue

                        val packet = ClientboundSectionBlocksUpdatePacket(secPos, localPositions, section)
                        level.chunkSource.chunkMap.getPlayers(secPos.chunk(), false).forEach { player ->
                            player.connection.send(packet)
                        }
                    } catch (_: Exception) {
                    }
                }

                for ((posLong, causeInfo) in notifySet) {
                    try {
                        val notifyPos = BlockPos.of(posLong)
                        val (fromPos, fromState) = causeInfo
                        level.neighborChanged(notifyPos, fromState.block, fromPos)
                    } catch (_: Exception) {
                    }
                }
            }

            delay(1.milliseconds)
        }
    }

    suspend fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newState: BlockState,
        updateBlock: Boolean = false,
    ) {
        val change = BlockChange(BlockPos(x, y, z), newState, updateBlock)

        pendingChanges.incrementAndGet()
        blockChannel.send(change)

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
        fun getInstance(level: ServerLevel): BlockChanger {
            return instances.computeIfAbsent(level.dimension()) { BlockChanger(level) }
        }

        @JvmStatic
        fun shutdownAll() {
            instances.values.forEach { it.shutdown() }
            instances.clear()
        }
    }
}