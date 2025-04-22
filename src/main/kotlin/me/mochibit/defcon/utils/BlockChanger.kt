/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.utils

import me.mochibit.defcon.Defcon
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.bukkit.scheduler.BukkitTask
import org.joml.Vector3i
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min

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
)

/**
 * Worker responsible for processing a queue of block changes
 */
class BlockChangeWorker(
    private val world: World,
    private val queue: ConcurrentLinkedQueue<BlockChange>,
    private val queueSize: AtomicInteger,
    private val blocksPerTick: Int,
    private val tickInterval: Long,
    private val plugin: Defcon
) {
    private var task: BukkitTask? = null
    private var running = false
    private val totalProcessed = AtomicInteger(0)
    private val chunkCache = ChunkCache.getInstance(world)
    private val stateLock = ReentrantLock()
    private var pauseThreshold = 100 // Auto-pause if server TPS drops
    private var autoRestartDelay = 60L // Ticks to wait before auto-restart
    private var consecutiveEmptyTicks = 0
    private val maxConsecutiveEmptyTicks = 5 // Stop after this many empty tick

    val isRunning get() = running

    /**
     * Start processing the queue
     */
    fun start() {
        stateLock.lock()
        try {
            if (running) return
            running = true
            consecutiveEmptyTicks = 0

            task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                val serverTPS = getServerTPS()
                val currentQueueSize = queueSize.get()

                // Auto-pause if server is struggling
                if (serverTPS < 16.0 && currentQueueSize > pauseThreshold) {
                    pauseProcessing()
                    // Schedule restart after delay
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        if (!running && queueSize.get() > 0) {
                            resumeProcessing()
                        }
                    }, autoRestartDelay)
                    return@Runnable
                }

                var processedCount = 0
                val maxToProcess = calculateDynamicBlocksPerTick(serverTPS, blocksPerTick)

                // Process blocks with adaptive rate based on server performance
                while (processedCount < maxToProcess && !queue.isEmpty()) {
                    val blockChange = queue.poll() ?: break
                    try {
                        applyBlockChange(blockChange)
                        queueSize.decrementAndGet()
                        processedCount++
                    } catch (e: Exception) {
                        plugin.logger.warning("Error processing block change at (${blockChange.x}, ${blockChange.y}, ${blockChange.z}): ${e.message}")
                    }
                }

                totalProcessed.addAndGet(processedCount)

                // Handle queue empty case
                if (queue.isEmpty()) {
                    consecutiveEmptyTicks++
                    if (consecutiveEmptyTicks >= maxConsecutiveEmptyTicks) {
                        stop()
                    }
                } else {
                    consecutiveEmptyTicks = 0
                }
            }, tickInterval, tickInterval)
        } finally {
            stateLock.unlock()
        }
    }

    /**
     * Stop processing
     */
    fun stop() {
        stateLock.lock()
        try {
            if (!running) return
            running = false
            task?.cancel()
            task = null
        } finally {
            stateLock.unlock()
        }
    }

    /**
     * Pause processing temporarily
     */
    private fun pauseProcessing() {
        stateLock.lock()
        try {
            if (!running) return
            running = false
            task?.cancel()
            task = null
        } finally {
            stateLock.unlock()
        }
    }

    /**
     * Resume processing after pause
     */
    private fun resumeProcessing() {
        start()
    }

    /**
     * Get server TPS (Transactions Per Second)
     */
    private fun getServerTPS(): Double {
        return 20.0
    }

    /**
     * Calculate dynamic blocks per tick based on server performance
     */
    private fun calculateDynamicBlocksPerTick(serverTPS: Double, baseBlocksPerTick: Int): Int {
        return when {
            serverTPS > 19.5 -> (baseBlocksPerTick * 1.25).toInt() // Server running well, process more
            serverTPS > 18.0 -> baseBlocksPerTick                  // Normal rate
            serverTPS > 16.0 -> (baseBlocksPerTick * 0.75).toInt() // Server struggling a bit, slow down
            serverTPS > 14.0 -> (baseBlocksPerTick * 0.5).toInt()  // Server under load, slow down more
            else -> (baseBlocksPerTick * 0.25).toInt()             // Server struggling significantly
        }.coerceAtLeast(10) // Always process at least 10 blocks
    }

    /**
     * Apply a block change efficiently
     */
    private fun applyBlockChange(change: BlockChange) {
        // Skip if block is already the target material
        if (chunkCache.getBlockMaterial(change.x, change.y, change.z) == change.newMaterial) return

        // Capture block data before changing material if needed
        val block: Block = world.getBlockAt(change.x, change.y, change.z)
        val oldBlockData = if (change.copyBlockData) block.blockData else null

        // Apply material change
        change.newMaterial?.let { block.setType(it, change.updateBlock) }

        // Apply block data if needed
        if (change.copyBlockData && oldBlockData != null) {
            try {
                val newBlockData = block.blockData
                copyRelevantBlockData(oldBlockData, newBlockData)
                block.setBlockData(newBlockData, false)
            } catch (e: Exception) {
                // Log but continue processing
                plugin.logger.fine("Failed to copy block data at (${change.x}, ${change.y}, ${change.z}): ${e.message}")
            }
        }

        change.newBiome?.let {
            world.setBiome(change.x, change.y, change.z, it)
        }
    }


    /**
     * Copy only the relevant block data properties
     */
    private fun copyRelevantBlockData(oldBlockData: BlockData, newBlockData: BlockData) {
        // Only copy properties that exist in both block data types
        try {
            if (oldBlockData is Directional && newBlockData is Directional) {
                newBlockData.facing = oldBlockData.facing
            }
            if (oldBlockData is Rail && newBlockData is Rail) {
                newBlockData.shape = oldBlockData.shape
            }
            if (oldBlockData is Bisected && newBlockData is Bisected) {
                newBlockData.half = oldBlockData.half
            }
            if (oldBlockData is Orientable && newBlockData is Orientable) {
                newBlockData.axis = oldBlockData.axis
            }
            if (oldBlockData is Rotatable && newBlockData is Rotatable) {
                newBlockData.rotation = oldBlockData.rotation
            }
            if (oldBlockData is Snowable && newBlockData is Snowable) {
                newBlockData.isSnowy = oldBlockData.isSnowy
            }
            if (oldBlockData is Waterlogged && newBlockData is Waterlogged) {
                newBlockData.isWaterlogged = oldBlockData.isWaterlogged
            }
            // Additional block data types
            if (oldBlockData is Openable && newBlockData is Openable) {
                newBlockData.isOpen = oldBlockData.isOpen
            }
            if (oldBlockData is Powerable && newBlockData is Powerable) {
                newBlockData.isPowered = oldBlockData.isPowered
            }
            if (oldBlockData is Ageable && newBlockData is Ageable) {
                newBlockData.age = oldBlockData.age.coerceAtMost(newBlockData.maximumAge)
            }
        } catch (e: Exception) {
            // Silently catch exceptions from incompatible properties
        }
    }

    /**
     * Configure worker parameters
     */
    fun configure(newBlocksPerTick: Int, newPauseThreshold: Int, newAutoRestartDelay: Long) {
        this.pauseThreshold = newPauseThreshold
        this.autoRestartDelay = newAutoRestartDelay
    }
}

/**
 * Optimized BlockChanger that manages block changes for a specific world
 */
class BlockChanger(private val world: World) {
    // Worker pool configuration
    private var workerCount = max(1, Runtime.getRuntime().availableProcessors() / 2)
    private var blocksPerWorkerTick = 500
    private var tickInterval = 1L
    private var pauseThreshold = 10000
    private var autoRestartDelay = 60L
    private val priorityQueue = ConcurrentLinkedQueue<BlockChange>()
    private val priorityQueueSize = AtomicInteger(0)

    // Worker pool and distribution
    private val workers = ArrayList<BlockChangeWorker>()
    private val queues = ArrayList<ConcurrentLinkedQueue<BlockChange>>()
    private val queueSizes = ArrayList<AtomicInteger>()
    private val configLock = ReentrantLock()
    private var taskLoadBalancer: BukkitTask? = null

    private var nextWorkerIndex = 0
    private val workerUsageStats = ArrayList<Double>() // Track worker efficiency

    init {
        initializeWorkers()
        startLoadBalancer()
    }

    /**
     * Singleton pattern implementation
     */
    companion object {
        private val instances = ConcurrentHashMap<String, BlockChanger>()
        private val instanceLock = ReentrantLock()

        /**
         * Get or create a BlockChanger instance for the specified world
         */
        @JvmStatic
        fun getInstance(world: World): BlockChanger {
            val worldName = world.name
            var instance = instances[worldName]

            if (instance == null) {
                instanceLock.lock()
                try {
                    // Double-check after acquiring lock
                    instance = instances[worldName]
                    if (instance == null) {
                        instance = BlockChanger(world)
                        instances[worldName] = instance
                    }
                } finally {
                    instanceLock.unlock()
                }
            }

            return instance!!
        }

        /**
         * Cleanup all instances - call this on plugin disable
         */
        @JvmStatic
        fun shutdownAll() {
            instanceLock.lock()
            try {
                for (instance in instances.values) {
                    instance.stopAll()
                }
                instances.clear()
            } finally {
                instanceLock.unlock()
            }
        }
    }

    /**
     * Initialize workers based on current configuration
     */
    private fun initializeWorkers() {
        configLock.lock()
        try {
            // Clean up existing workers if any
            stopAll()
            workers.clear()
            queues.clear()
            queueSizes.clear()
            workerUsageStats.clear()

            // Create new workers
            for (i in 0 until workerCount) {
                val queue = ConcurrentLinkedQueue<BlockChange>()
                val queueSize = AtomicInteger(0)
                queues.add(queue)
                queueSizes.add(queueSize)
                workerUsageStats.add(0.0)

                val worker = BlockChangeWorker(
                    world,
                    queue,
                    queueSize,
                    blocksPerWorkerTick,
                    tickInterval,
                    Defcon.instance
                )
                worker.configure(blocksPerWorkerTick, pauseThreshold, autoRestartDelay)
                workers.add(worker)
            }
        } finally {
            configLock.unlock()
        }
    }

    fun stopAll() {
        configLock.lock()
        try {
            // Stop all workers first
            for (worker in workers) {
                worker.stop()
            }

            // Cancel load balancer task
            taskLoadBalancer?.cancel()
            taskLoadBalancer = null

            // Clear any remaining queues
            if (queues.isNotEmpty()) {
                for (i in 0 until queues.size) {
                    // Don't lose track of pending changes
                    val remainingChanges = queues[i].size
                    if (remainingChanges > 0) {
                        priorityQueue.addAll(queues[i])
                        priorityQueueSize.addAndGet(remainingChanges)
                    }
                    queues[i].clear()
                    queueSizes[i].set(0)
                }
            }
        } finally {
            configLock.unlock()
        }
    }

    /**
     * Find the queue with the least number of pending changes
     * and update usage stats for adaptive balancing
     */
    private fun findBestWorkerIndex(): Int {
        var bestIndex = 0
        var minSize = Int.MAX_VALUE
        var totalWork = 0

        for (i in 0 until workerCount) {
            val size = queueSizes[i].get()
            totalWork += size

            // We also consider worker historical efficiency in our calculation
            val adjustedSize = (size * (1.0 + workerUsageStats[i] * 0.2)).toInt()

            if (adjustedSize < minSize) {
                bestIndex = i
                minSize = adjustedSize
            }
        }

        // Update worker usage statistics (simple exponential moving average)
        if (totalWork > 0) {
            for (i in 0 until workerCount) {
                val currentRatio = queueSizes[i].get().toDouble() / totalWork
                workerUsageStats[i] = workerUsageStats[i] * 0.8 + currentRatio * 0.2
            }
        }

        return bestIndex
    }

    /**
     * Improved worker selection for batch operations
     * Uses a combination of round-robin and least-loaded strategies
     */
    private fun getNextWorkerIndex(): Int {
        // For every 4th selection, use the least loaded worker instead of round-robin
        return if (nextWorkerIndex % 4 == 0) {
            val index = findBestWorkerIndex()
            nextWorkerIndex++
            index
        } else {
            val index = (nextWorkerIndex++) % workerCount
            if (nextWorkerIndex >= workerCount * 2) nextWorkerIndex = 0
            index
        }
    }

    /**
     * Add a block change using x, y, z coordinates
     */
    fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material?,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome? = null
    ) {
        val workerIndex = findBestWorkerIndex()
        val queue = queues[workerIndex]
        val queueSize = queueSizes[workerIndex]

        queue.add(BlockChange(x, y, z, newMaterial, copyBlockData, updateBlock, newBiome))
        queueSize.incrementAndGet()

        // Start worker if not already running
        val worker = workers[workerIndex]
        if (!worker.isRunning)
            worker.start()
    }

    /**
     * Add a block change using Block object
     */
    fun addBlockChange(
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

    fun addBlockChange(
        pos: Vector3i,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false,
        newBiome: Biome?
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

    fun changeBiome(
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
            newBiome
        )
    }

    /**
     * Bulk add block changes - more efficient for large operations
     * Now with smarter distribution based on change locality
     */
    fun addBlockChanges(changes: Collection<BlockChange>) {
        if (changes.isEmpty()) return

        // For small batches, distribute evenly
        if (changes.size < 100) {
            val workerIndex = getNextWorkerIndex()
            val queue = queues[workerIndex]
            val queueSize = queueSizes[workerIndex]

            queue.addAll(changes)
            queueSize.addAndGet(changes.size)

            // Start worker if not already running
            val worker = workers[workerIndex]
            if (!worker.isRunning)
                worker.start()
            return
        }

        // For large batches, distribute based on spatial locality when possible
        // This improves chunk loading/caching efficiency
        try {
            // Group by chunk coordinates (simple division by 16)
            val changesByChunk = changes.groupBy { Triple(it.x shr 4, it.y shr 4, it.z shr 4) }

            // Distribute chunks across workers
            val chunkBatches = changesByChunk.values.chunked(max(1, changesByChunk.size / workerCount))

            for (chunkGroup in chunkBatches) {
                val workerIndex = getNextWorkerIndex()
                val queue = queues[workerIndex]
                val queueSize = queueSizes[workerIndex]
                val batchSize = chunkGroup.sumOf { it.size }

                for (chunk in chunkGroup) {
                    queue.addAll(chunk)
                }
                queueSize.addAndGet(batchSize)

                // Start worker if not already running
                val worker = workers[workerIndex]
                if (!worker.isRunning)
                    worker.start()
            }
        } catch (e: Exception) {
            // Fallback to simple distribution if spatial grouping fails
            val changesPerWorker = changes.size / workerCount + 1
            val batches = changes.chunked(changesPerWorker)

            for (batch in batches) {
                val workerIndex = getNextWorkerIndex()
                val queue = queues[workerIndex]
                val queueSize = queueSizes[workerIndex]

                queue.addAll(batch)
                queueSize.addAndGet(batch.size)

                // Start worker if not already running
                val worker = workers[workerIndex]
                if (!worker.isRunning)
                    worker.start()
            }
        }
    }

    /**
     * Start the load balancer that periodically rebalances work across workers
     */
    private fun startLoadBalancer() {
        taskLoadBalancer = Defcon.instance.server.scheduler.runTaskTimer(
            Defcon.instance,
            Runnable { rebalanceQueues() },
            100L, // Initial delay
            200L  // Run every 10 seconds (200 ticks)
        )
    }

    /**
     * Rebalance the queues to ensure even distribution of work
     * More efficient implementation with better thresholds
     */
    private fun rebalanceQueues() {
        configLock.lock()
        try {
            if (workers.isEmpty()) return

            // Calculate total and average work
            var totalWork = 0
            var maxWork = 0
            var minWork = Int.MAX_VALUE

            for (size in queueSizes) {
                val work = size.get()
                totalWork += work
                maxWork = max(maxWork, work)
                minWork = min(minWork, work)
            }

            // Only rebalance if there's significant imbalance
            if (totalWork < 200 || maxWork - minWork < 150) return

            val targetPerWorker = totalWork / workerCount

            // Find workers with too much work and those with too little
            val workersWithExcess = mutableListOf<Pair<Int, Int>>() // Pair<Index, ExcessWork>
            val workersWithCapacity = mutableListOf<Pair<Int, Int>>() // Pair<Index, AvailableCapacity>

            for (i in 0 until workerCount) {
                val currentWork = queueSizes[i].get()
                val difference = currentWork - targetPerWorker

                if (difference > 100) { // Only redistribute significant imbalances
                    workersWithExcess.add(Pair(i, difference))
                } else if (difference < -100) {
                    workersWithCapacity.add(Pair(i, -difference))
                }
            }

            // Sort by most excess and most capacity
            workersWithExcess.sortByDescending { it.second }
            workersWithCapacity.sortByDescending { it.second }

            // Redistribute work
            for ((excessWorkerIdx, excess) in workersWithExcess) {
                if (workersWithCapacity.isEmpty()) break

                val (capacityWorkerIdx, capacity) = workersWithCapacity.removeAt(0)
                val toRedistribute = min(excess / 2, capacity) // Take half of excess, up to capacity

                if (toRedistribute < 50) continue // Don't bother with small redistributions

                // Move blocks between queues
                val sourceQueue = queues[excessWorkerIdx]
                val targetQueue = queues[capacityWorkerIdx]
                val sourceSizeCounter = queueSizes[excessWorkerIdx]
                val targetSizeCounter = queueSizes[capacityWorkerIdx]

                val transferred = transferBlocks(sourceQueue, targetQueue, toRedistribute)
                if (transferred > 0) {
                    sourceSizeCounter.addAndGet(-transferred)
                    targetSizeCounter.addAndGet(transferred)
                }
            }
        } finally {
            configLock.unlock()
        }
    }

    /**
     * Transfer a specific number of block changes from one queue to another
     */
    private fun transferBlocks(
        sourceQueue: ConcurrentLinkedQueue<BlockChange>,
        targetQueue: ConcurrentLinkedQueue<BlockChange>,
        count: Int
    ): Int {
        var transferred = 0
        val tempList = ArrayList<BlockChange>(count)

        // Get blocks from source queue in batch
        repeat(count) {
            val change = sourceQueue.poll() ?: return transferred
            tempList.add(change)
            transferred++
        }

        // Add to target queue in bulk
        targetQueue.addAll(tempList)
        return transferred
    }

    /**
     * Configure worker parameters
     */
    fun configure(
        newWorkerCount: Int? = null,
        newBlocksPerTick: Int? = null,
        newTickInterval: Long? = null,
        newPauseThreshold: Int? = null,
        newAutoRestartDelay: Long? = null
    ) {
        configLock.lock()
        try {
            var needsReinit = false

            newWorkerCount?.let {
                if (it > 0 && it != workerCount) {
                    workerCount = it
                    needsReinit = true
                }
            }

            newBlocksPerTick?.let {
                if (it > 0) {
                    blocksPerWorkerTick = it
                    for (worker in workers) {
                        worker.configure(blocksPerWorkerTick, pauseThreshold, autoRestartDelay)
                    }
                }
            }

            newTickInterval?.let {
                if (it > 0 && it != tickInterval) {
                    tickInterval = it
                    needsReinit = true
                }
            }

            newPauseThreshold?.let {
                pauseThreshold = it
                for (worker in workers) {
                    worker.configure(blocksPerWorkerTick, pauseThreshold, autoRestartDelay)
                }
            }

            newAutoRestartDelay?.let {
                autoRestartDelay = it
                for (worker in workers) {
                    worker.configure(blocksPerWorkerTick, pauseThreshold, autoRestartDelay)
                }
            }

            if (needsReinit) {
                initializeWorkers()
            }
        } finally {
            configLock.unlock()
        }
    }
}