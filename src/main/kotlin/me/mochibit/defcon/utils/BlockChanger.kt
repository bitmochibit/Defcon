package me.mochibit.defcon.utils

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.Defcon
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight block change representation
 */
data class BlockChange(
    val x: Int,
    val y: Int,
    val z: Int,
    val newMaterial: Material? = null,
    val copyBlockData: Boolean = false,
    val updateBlock: Boolean = false,
    val newBiome: Biome? = null,
) {
    // Add chunk coordinates for faster access
    private val chunkX: Int = x shr 4
    private val chunkZ: Int = z shr 4

    // Generate chunk key directly
    val chunkKey: Long = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
}

/**
 * Optimized BlockChanger using Kotlin Channels and Coroutines for efficient processing
 */
class BlockChanger private constructor(
    private val world: World,
) {
    private val plugin = Defcon.instance
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Configuration properties with more aggressive defaults
    private var blocksPerBatch = 25000 * 5
    private var processingInterval = 0L
    private var maxQueueSize = 500000
    private var chunkBatchSize = 1000
    private var workerCount = 16

    // Stats tracking
    private val totalProcessed = AtomicLong(0)
    private val processingActive = AtomicBoolean(false)
    private val currentQueueSize = AtomicLong(0)

    // Cache reference
    private val chunkCache = ChunkCache.getInstance(world)

    // Single shared channel for all workers
    private lateinit var blockChannel: Channel<BlockChange>
    private var processingJobs = mutableListOf<Job>()

    init {
        initializeChannel()
        startProcessing()
    }

    /**
     * Initialize the channel system
     */
    private fun initializeChannel() {
        // Create a single buffered channel with high capacity
        blockChannel = Channel(maxQueueSize)
    }

    /**
     * Start processing block changes with multiple workers
     */
    private fun startProcessing() {
        if (!processingActive.compareAndSet(false, true)) return

        processingJobs.clear()

        // Launch multiple workers that share the same channel
        repeat(workerCount) { workerId ->
            val job = scope.launch {
                processBlocks(workerId)
            }
            processingJobs.add(job)
        }
    }

    /**
     * Process blocks from the shared channel
     */
    private suspend fun processBlocks(workerId: Int) {
        // Reusable collections to minimize allocations
        val chunksToProcess = HashMap<Long, MutableList<BlockChange>>(64)

        while (true) {
            try {
                // Clear previous batch data
                chunksToProcess.clear()

                // Collect a batch of changes from the channel
                var batchSize = 0
                val targetBatchSize = blocksPerBatch / workerCount

                // Fast collection loop with yield for cooperative behavior
                while (batchSize < targetBatchSize) {
                    val change = blockChannel.tryReceive().getOrNull() ?: break
                    chunksToProcess.getOrPut(change.chunkKey) { ArrayList(64) }.add(change)
                    batchSize++
                    currentQueueSize.decrementAndGet()

                    // Periodically yield to allow other coroutines to run
                    if (batchSize % 5000 == 0) yield()
                }

                if (chunksToProcess.isEmpty()) {
                    // If nothing in immediate queue, do a suspending receive
                    val change = blockChannel.receiveCatching().getOrNull() ?: continue
                    chunksToProcess.getOrPut(change.chunkKey) { ArrayList(64) }.add(change)
                    currentQueueSize.decrementAndGet()
                }

                // Process chunks more aggressively
                val chunkEntries = chunksToProcess.entries.take(chunkBatchSize)

                // Process all collected chunks on the main thread in one batch
                withContext(plugin.minecraftDispatcher) {
                    for ((_, chunkChanges) in chunkEntries) {
                        // Process all changes for this chunk
                        for (change in chunkChanges) {
                            applyBlockChange(change)
                        }
                        totalProcessed.addAndGet(chunkChanges.size.toLong())
                    }
                }

                // Dynamic delay based on system load
                if (currentQueueSize.get() < maxQueueSize / 20) {
                    if (processingInterval > 0) delay(processingInterval)
                    else yield() // Just yield when processing interval is 0
                }

            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                if (plugin.config.getBoolean("debug", false)) {
                    plugin.logger.warning("Error processing blocks in worker $workerId: ${e.message}")
                }
                yield() // Just yield on error to continue processing
            }
        }
    }

    /**
     * Apply a single block change efficiently
     */
    private fun applyBlockChange(change: BlockChange) {
        try {
            // Skip if block is already the target material (fast path using cache)
            if (change.newMaterial != null &&
                chunkCache.getBlockMaterial(change.x, change.y, change.z) == change.newMaterial
            ) {
                return
            }

            // Get block and apply changes
            val block = world.getBlockAt(change.x, change.y, change.z)

            // Capture block data before changing material if needed
            val oldBlockData = if (change.copyBlockData) block.blockData else null

            // Apply material change
            change.newMaterial?.let {
                block.setType(it, change.updateBlock)
            }

            // Apply block data if needed
            if (change.copyBlockData && oldBlockData != null && change.newMaterial != null) {
                try {
                    val newBlockData = block.blockData
                    copyRelevantBlockData(oldBlockData, newBlockData)
                    block.setBlockData(newBlockData, false)
                } catch (e: Exception) {
                    // Silent catch for better performance
                }
            }

            // Apply biome change if requested
            change.newBiome?.let {
                world.setBiome(change.x, change.y, change.z, it)
            }
        } catch (e: Exception) {
            // Silent catch for resilience against world/chunk unload scenarios
        }
    }

    /**
     * Copy only the relevant block data properties with memory-efficient implementation
     */
    private fun copyRelevantBlockData(oldBlockData: BlockData, newBlockData: BlockData) {
        try {
            // Only copy properties that exist in both block data types
            // Use more type checking to avoid unnecessary casts
            if (oldBlockData is Directional && newBlockData is Directional) {
                try {
                    newBlockData.facing = oldBlockData.facing
                } catch (e: Exception) {
                }
            }
            if (oldBlockData is Bisected && newBlockData is Bisected) {
                try {
                    newBlockData.half = oldBlockData.half
                } catch (e: Exception) {
                }
            }
            if (oldBlockData is Orientable && newBlockData is Orientable) {
                try {
                    newBlockData.axis = oldBlockData.axis
                } catch (e: Exception) {
                }
            }
            if (oldBlockData is Rotatable && newBlockData is Rotatable) {
                try {
                    newBlockData.rotation = oldBlockData.rotation
                } catch (e: Exception) {
                }
            }

            // Only check boolean properties if we need to change them (avoid extra calls)
            if (oldBlockData is Waterlogged && newBlockData is Waterlogged && oldBlockData.isWaterlogged) {
                newBlockData.isWaterlogged = true
            }
            if (oldBlockData is Snowable && newBlockData is Snowable && oldBlockData.isSnowy) {
                newBlockData.isSnowy = true
            }
            if (oldBlockData is Openable && newBlockData is Openable && oldBlockData.isOpen) {
                newBlockData.isOpen = true
            }
            if (oldBlockData is Powerable && newBlockData is Powerable && oldBlockData.isPowered) {
                newBlockData.isPowered = true
            }

            // Rail is expensive to check, only do it if both are rails
            if (oldBlockData is Rail && newBlockData is Rail) {
                try {
                    newBlockData.shape = oldBlockData.shape
                } catch (_: Exception) { }
            }

            // Age is expensive due to coercing, do last
            if (oldBlockData is Ageable && newBlockData is Ageable) {
                val targetAge = oldBlockData.age.coerceAtMost(newBlockData.maximumAge)
                if (targetAge > 0) { // Skip if age is 0 (default)
                    newBlockData.age = targetAge
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Add a block change using x, y, z coordinates - suspending for backpressure
     */
    suspend fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material?,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        val change = BlockChange(x, y, z, newMaterial, copyBlockData, updateBlock, newBiome)

        // Send to channel - will suspend if channel is full (built-in backpressure)
        blockChannel.send(change)
        currentQueueSize.incrementAndGet()

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
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        addBlockChange(
            block.x,
            block.y,
            block.z,
            newMaterial,
            copyBlockData,
            updateBlock,
            newBiome
        )
    }

    /**
     * Add a block change using Vector3i object
     */
    suspend fun addBlockChange(
        pos: Vector3i,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        addBlockChange(
            pos.x,
            pos.y,
            pos.z,
            newMaterial,
            copyBlockData,
            updateBlock,
            newBiome
        )
    }

    /**
     * Change only the biome at a location
     */
    suspend fun changeBiome(
        x: Int,
        y: Int,
        z: Int,
        newBiome: Biome
    ) {
        addBlockChange(
            x,
            y,
            z,
            newMaterial = null,
            copyBlockData = false,
            updateBlock = false,
            newBiome = newBiome
        )
    }

    /**
     * Bulk add block changes - optimized with larger batches
     */
    suspend fun addBlockChanges(changes: Collection<BlockChange>) {
        if (changes.isEmpty()) return

        val batchSize = 20000

        for (batch in changes.chunked(batchSize)) {
            // Submit batch through the channel with counter updates
            for (change in batch) {
                blockChannel.send(change)
                currentQueueSize.incrementAndGet()
            }

            // Brief yield after each batch
            if (batch.size == batchSize) {
                yield()
            }
        }

        // Ensure processing is active
        if (!processingActive.get()) {
            startProcessing()
        }
    }

    /**
     * Non-suspending version for use in synchronous contexts
     */
    fun addBlockChangeSync(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material?,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ): Boolean {
        val change = BlockChange(x, y, z, newMaterial, copyBlockData, updateBlock, newBiome)

        // Try to add with non-blocking approach
        val added = blockChannel.trySend(change).isSuccess

        if (added) {
            currentQueueSize.incrementAndGet()

            // Ensure processing is active
            if (!processingActive.get()) {
                scope.launch { startProcessing() }
            }
        }

        return added
    }

    /**
     * Configure block changer parameters
     */
    fun configure(
        newBlocksPerBatch: Int? = null,
        newProcessingInterval: Long? = null,
        newMaxQueueSize: Int? = null,
        newWorkerCount: Int? = null,
        newChunkBatchSize: Int? = null
    ) {
        var needRestart = false

        newBlocksPerBatch?.let { if (it > 0) blocksPerBatch = it }
        newProcessingInterval?.let { if (it >= 0) processingInterval = it }
        newChunkBatchSize?.let { if (it > 0) chunkBatchSize = it }

        // Worker count requires restart if changed
        newWorkerCount?.let {
            if (it > 0 && it != workerCount) {
                workerCount = it
                needRestart = true
            }
        }

        // Queue size requires new channel if changed
        newMaxQueueSize?.let {
            if (it > 0 && it != maxQueueSize) {
                maxQueueSize = it
                needRestart = true
            }
        }

        if (needRestart && processingActive.get()) {
            shutdown()
            initializeChannel() // Recreate channel with new capacity
            startProcessing()
        }
    }

    /**
     * Get the current queue size
     */
    fun getQueueSize(): Long {
        return currentQueueSize.get()
    }

    /**
     * Get the total number of blocks processed
     */
    fun getTotalProcessed(): Long {
        return totalProcessed.get()
    }

    /**
     * Clean method to flush all pending changes before shutdown
     */
    suspend fun flush() {
        // Wait until queue is empty
        while (currentQueueSize.get() > 0) {
            delay(50)
        }
    }

    /**
     * Stop processing and clean up resources
     */
    fun shutdown() {
        processingActive.set(false)

        // Cancel all processing jobs
        processingJobs.forEach { it.cancel() }
        processingJobs.clear()

        // Close channel
        blockChannel.close()
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

/**
 * Extension method for Defcon class for convenient access
 */
fun Defcon.getBlockChanger(world: World): BlockChanger {
    return BlockChanger.getInstance(world)
}