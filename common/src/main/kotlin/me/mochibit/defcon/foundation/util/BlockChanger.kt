package me.mochibit.defcon.foundation.util


import it.unimi.dsi.fastutil.shorts.ShortArraySet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.async.withMainContext
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
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
    private val level: ServerLevel,
) {

    private val workerScope =
        CoroutineScope(ServerCoroutineScope.coroutineContext + SupervisorJob(ServerCoroutineScope.coroutineContext[Job]))

    private val workerCount = 1
    private val batchSize = 1000
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

                            try {
                                batch.add(change)
                                pendingChanges.decrementAndGet()
                                collected++
                            } catch (e: Exception) {
                                pendingChanges.decrementAndGet()
                            }
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

                        section.setBlockState(localX, localY, localZ, change.newState, false)
                        chunk.isUnsaved = true

                        if (!oldState.isAir && change.newState.isAir) {
                            val abovePos = pos.above()
                            val aboveState = level.getBlockState(abovePos)
                            if (!aboveState.isAir) {
                                level.neighborChanged(abovePos, oldState.block, pos)
                            }
                        }

                        val secPos = SectionPos.of(pos)
                        val (localPositions, _) = sectionUpdates.getOrPut(secPos) {
                            Pair(ShortArraySet(), section)
                        }


                        val shortLocation = SectionPos.sectionRelativePos(pos)
                        localPositions.add(shortLocation)

                    } catch (_: Exception) {}
                }

                for ((secPos, data) in sectionUpdates) {
                    try {
                        val localPositions = data.first
                        val section = data.second

                        if (localPositions.isEmpty()) continue

                        val packet = ClientboundSectionBlocksUpdatePacket(
                            secPos,
                            localPositions,
                            section
                        )

                        level.chunkSource.chunkMap.getPlayers(secPos.chunk(), false).forEach { player ->
                            player.connection.send(packet)
                        }
                    } catch (_: Exception) {}
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