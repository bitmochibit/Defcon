package me.mochibit.defcon.utils

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.mochibit.defcon.Defcon
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Openable
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.Powerable
import org.bukkit.block.data.Rail
import org.bukkit.block.data.Rotatable
import org.bukkit.block.data.Snowable
import org.bukkit.block.data.Waterlogged
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight block change representation
 */
private data class BlockChange(
    val x: Int,
    val y: Int,
    val z: Int,
    val newMaterial: Material,
    val updateBlock: Boolean,
)

/**
 * Optimized BlockChanger using Kotlin Channels and Coroutines for fast parallel processing
 */
class BlockChanger private constructor(
    private val world: World,
) {
    private val plugin = Defcon
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Configuration
    private val workerCount = 4 // Workers to process and batch blocks
    private val batchSize = 10000 // Apply blocks in batches for performance

    // State
    private val processingActive = AtomicBoolean(false)
    private val pendingChanges =
        java.util.concurrent.atomic
            .AtomicLong(0)

    // Channel for block changes with large buffer for better throughput
    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()

    init {
        initializeChannel()
        startProcessing()
    }

    /**
     * Initialize the channel system with large buffer
     */
    private fun initializeChannel() {
        blockChannel = Channel(Channel.UNLIMITED) // Unlimited buffer for maximum speed
    }

    /**
     * Start processing block changes with batching for massive performance improvement
     */
    private fun startProcessing() {
        if (!processingActive.compareAndSet(false, true)) return

        processingJobs.clear()

        // Launch workers that batch blocks before applying to main thread
        repeat(workerCount) {
            val job =
                scope.launch(Dispatchers.Default) {
                    val batch = mutableListOf<Pair<BlockChange, BlockData>>()

                    for (change in blockChannel) {
                        try {
                            // Prepare block data on background thread
                            val oldMaterial = NMSReflectionCache.getBlockMaterial(world, change.x, change.y, change.z)
                            val newBlockData = change.newMaterial.createBlockData()

                            // Copy relevant properties if materials are compatible
                            if (oldMaterial != change.newMaterial) {
                                val oldBlockData = oldMaterial.createBlockData()
                                copyRelevantBlockData(oldBlockData, newBlockData)
                            }

                            batch.add(change to newBlockData)

                            // Apply batch when it reaches size limit
                            if (batch.size >= batchSize) {
                                applyBatch(batch.toList())
                                batch.clear()
                            }
                        } catch (_: Exception) {
                            // Silent catch for resilience
                        } finally {
                            pendingChanges.decrementAndGet()
                        }
                    }

                    // Apply any remaining blocks in the batch
                    if (batch.isNotEmpty()) {
                        applyBatch(batch.toList())
                    }
                }
            processingJobs.add(job)
        }
    }

    /**
     * Apply a batch of prepared block changes on the main thread - much faster than individual calls
     */
    private suspend fun applyBatch(batch: List<Pair<BlockChange, BlockData>>) {
        kotlinx.coroutines.withContext(plugin.minecraftDispatcher) {
            for ((change, blockData) in batch) {
                try {
                    NMSReflectionCache.setBlockFast(world, change.x, change.y, change.z, blockData)
                } catch (_: Exception) {
                    // Silent catch for resilience
                }
            }
        }
    }

    /**
     * Copy only the relevant block data properties with memory-efficient implementation.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun copyRelevantBlockData(
        oldBlockData: BlockData,
        newBlockData: BlockData,
    ) {
        try {
            // Directional properties
            if (oldBlockData is Directional && newBlockData is Directional) {
                runCatching { newBlockData.facing = oldBlockData.facing }
            }

            // Orientation properties
            when {
                oldBlockData is Bisected && newBlockData is Bisected -> {
                    runCatching { newBlockData.half = oldBlockData.half }
                }

                oldBlockData is Orientable && newBlockData is Orientable -> {
                    runCatching { newBlockData.axis = oldBlockData.axis }
                }

                oldBlockData is Rotatable && newBlockData is Rotatable -> {
                    runCatching { newBlockData.rotation = oldBlockData.rotation }
                }
            }

            // State properties
            if (oldBlockData is Waterlogged && newBlockData is Waterlogged && oldBlockData.isWaterlogged) {
                runCatching { newBlockData.isWaterlogged = true }
            }
            if (oldBlockData is Snowable && newBlockData is Snowable && oldBlockData.isSnowy) {
                runCatching { newBlockData.isSnowy = true }
            }
            if (oldBlockData is Openable && newBlockData is Openable && oldBlockData.isOpen) {
                runCatching { newBlockData.isOpen = true }
            }
            if (oldBlockData is Powerable && newBlockData is Powerable && oldBlockData.isPowered) {
                runCatching { newBlockData.isPowered = true }
            }

            // Rail shape
            if (oldBlockData is Rail && newBlockData is Rail) {
                runCatching { newBlockData.shape = oldBlockData.shape }
            }

            // Ageable
            if (oldBlockData is Ageable && newBlockData is Ageable) {
                val targetAge = oldBlockData.age.coerceAtMost(newBlockData.maximumAge)
                if (targetAge > 0) {
                    runCatching { newBlockData.age = targetAge }
                }
            }
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * Add a block change using x, y, z coordinates - suspending for backpressure
     */
    suspend fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material,
        updateBlock: Boolean = false,
    ) {
        val change = BlockChange(x, y, z, newMaterial, updateBlock)

        // Increment pending changes before sending
        pendingChanges.incrementAndGet()

        // Send to channel - will suspend if channel is full (built-in backpressure)
        blockChannel.send(change)

        // Ensure processing is active
        if (!processingActive.get()) {
            startProcessing()
        }
    }

    /**
     * Add a block change using Block object
     */
    suspend fun addBlockChange(
        block: Block,
        newMaterial: Material,
        updateBlock: Boolean = false,
    ) {
        addBlockChange(block.x, block.y, block.z, newMaterial, updateBlock)
    }

    /**
     * Add a block change using Vector3i object
     */
    suspend fun addBlockChange(
        pos: Vector3i,
        newMaterial: Material,
        updateBlock: Boolean = false,
    ) {
        addBlockChange(pos.x, pos.y, pos.z, newMaterial, updateBlock)
    }

    /**
     * Wait for all pending block changes to be processed.
     * This is crucial for ensuring changes are applied before subsequent operations.
     */
    suspend fun flush() {
        // Wait until all pending changes are processed
        while (pendingChanges.get() > 0) {
            kotlinx.coroutines.delay(10) // Small delay to avoid busy waiting
        }
    }

    /**
     * Stop processing and clean up resources
     */
    fun shutdown() {
        processingActive.set(false)

        // Cancel all processing jobs
        scope.launch {
            processingJobs.joinAll()
            processingJobs.clear()
            // Close channel
            blockChannel.close()
        }
    }

    /**
     * Singleton pattern implementation
     */
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
