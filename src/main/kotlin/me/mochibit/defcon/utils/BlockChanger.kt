package me.mochibit.defcon.utils

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.mochibit.defcon.Defcon
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private data class BlockChange(
    val x: Int,
    val y: Int,
    val z: Int,
    val newMaterial: Material,
    val updateBlock: Boolean,
)

class BlockChanger private constructor(
    private val world: World,
) {
    private val plugin = Defcon

    private val workerCount = 5
    private val batchSize = 60000
    private val batchTimeoutMs = 50L

    val chunkCache = ChunkCache.getInstance(world)

    // State
    private val processingActive = AtomicBoolean(false)
    private val pendingChanges = AtomicLong(0)

    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()

    init {
        initializeChannel()
        startProcessing()
    }

    private fun initializeChannel() {
        blockChannel = Channel(Channel.UNLIMITED)
    }

    private fun startProcessing() {
        if (!processingActive.compareAndSet(false, true)) return

        processingJobs.clear()

        repeat(workerCount) {
            val job =
                plugin.launch(Dispatchers.Default) {
                    val batch = ArrayList<Pair<BlockChange, Material>>(batchSize)
                    var lastBatchTime = System.currentTimeMillis()

                    while (processingActive.get() || !blockChannel.isEmpty) {
                        var collected = 0
                        val startTime = System.currentTimeMillis()

                        while (collected < batchSize &&
                            (System.currentTimeMillis() - startTime) < batchTimeoutMs
                        ) {
                            val change = blockChannel.tryReceive().getOrNull() ?: break

                            try {
                                batch.add(change to change.newMaterial)
                                pendingChanges.decrementAndGet()
                                collected++
                            } catch (e: Exception) {
                                pendingChanges.decrementAndGet()
                            }
                        }

                        if (batch.isNotEmpty()) {
                            applyBatchOptimized(batch.toList())
                            batch.clear()
                            lastBatchTime = System.currentTimeMillis()
                        } else {
                            delay(5L)
                        }
                    }

                    if (batch.isNotEmpty()) {
                        applyBatchOptimized(batch.toList())
                    }
                }
            processingJobs.add(job)
        }
    }

    private suspend fun applyBatchOptimized(batch: List<Pair<BlockChange, Material>>) {
        kotlinx.coroutines.withContext(plugin.minecraftDispatcher) {
            for ((change, material) in batch) {
                try {
                    val blockData = material.createBlockData()
//                    world
//                        .getBlockAt(change.x, change.y, change.z)
//                        .setBlockData(blockData, false)
                    NMSReflectionCache.setBlockFast(world, change.x, change.y, change.z, blockData)
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material,
        updateBlock: Boolean = false,
    ) {
        val change = BlockChange(x, y, z, newMaterial, updateBlock)

        pendingChanges.incrementAndGet()
        blockChannel.send(change)
//        chunkCache.updateBlockType(x, y, z, newMaterial)

        if (!processingActive.get()) {
            startProcessing()
        }
    }

    suspend fun flush() {
        while (pendingChanges.get() > 0) {
            delay(20)
        }

        delay(50)
    }

    fun shutdown() {
        processingActive.set(false)

        plugin.launch(Dispatchers.IO) {
            processingJobs.joinAll()
            processingJobs.clear()
            blockChannel.close()
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, BlockChanger>()

        @JvmStatic
        fun getInstance(world: World): BlockChanger {
            val worldName = world.name
            return instances.computeIfAbsent(worldName) { BlockChanger(world) }
        }

        @JvmStatic
        fun shutdownAll() {
            instances.values.forEach { it.shutdown() }
            instances.clear()
        }
    }
}
