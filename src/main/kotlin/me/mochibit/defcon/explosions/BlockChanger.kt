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
package me.mochibit.defcon.explosions

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.ChunkCache
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.bukkit.scheduler.BukkitTask
import org.joml.Vector3i
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
    val newMaterial: Material,
    val copyBlockData: Boolean = false,
    val updateBlock: Boolean = false
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
    private val maxConsecutiveEmptyTicks = 5 // Stop after this many empty ticks

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
        return try {
            // Try to get TPS from Spigot/Paper API
            val server = plugin.server
            val getTPSMethod = server.javaClass.getMethod("getTPS")
            val tpsArray = getTPSMethod.invoke(server) as DoubleArray
            tpsArray[0] // Get the 1-minute average TPS
        } catch (e: Exception) {
            // Default to assuming good TPS if we can't measure
            20.0
        }
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
        block.setType(change.newMaterial, false)

        // Apply block data if needed
        if (change.copyBlockData && oldBlockData != null) {
            try {
                val newBlockData = block.blockData
                copyRelevantBlockData(oldBlockData, newBlockData)
                block.setBlockData(newBlockData, change.updateBlock)
            } catch (e: Exception) {
                // Log but continue processing
                plugin.logger.fine("Failed to copy block data at (${change.x}, ${change.y}, ${change.z}): ${e.message}")
            }
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
     * Get performance metrics
     */
    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "queueSize" to queueSize.get(),
            "totalProcessed" to totalProcessed.get(),
            "isRunning" to running,
            "serverTPS" to getServerTPS()
        )
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

    init {
        initializeWorkers()
        startLoadBalancer()
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

            // Create new workers
            for (i in 0 until workerCount) {
                val queue = ConcurrentLinkedQueue<BlockChange>()
                val queueSize = AtomicInteger(0)
                queues.add(queue)
                queueSizes.add(queueSize)

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

    private fun stopAll() {
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
     */
    private fun findBestWorkerIndex(): Int {
        var bestIndex = 0
        var minSize = queueSizes[0].get()

        for (i in 1 until workerCount) {
            val size = queueSizes[i].get()
            if (size < minSize) {
                bestIndex = i
                minSize = size
            }
        }

        return bestIndex
    }

    /**
     * Round-robin worker selection for batch operations
     */
    private fun getNextWorkerIndex(): Int {
        val index = (nextWorkerIndex++) % workerCount
        if (nextWorkerIndex >= workerCount * 2) nextWorkerIndex = 0
        return index
    }

    /**
     * Add a block change using x, y, z coordinates
     */
    fun addBlockChange(
        x: Int,
        y: Int,
        z: Int,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false
    ) {
        val workerIndex = findBestWorkerIndex()
        val queue = queues[workerIndex]
        val queueSize = queueSizes[workerIndex]

        queue.add(BlockChange(x, y, z, newMaterial, copyBlockData, updateBlock))
        queueSize.incrementAndGet()

        // Start worker if not already running
        val worker = workers[workerIndex]
        if (!(worker.getMetrics()["isRunning"] as Boolean)) {
            worker.start()
        }
    }

    /**
     * Add a block change using Block object
     */
    fun addBlockChange(
        block: Block,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false
    ) {
        addBlockChange(
            block.x,
            block.y,
            block.z,
            newMaterial,
            copyBlockData,
            updateBlock
        )
    }

    fun addBlockChange(
        pos: Vector3i,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        updateBlock: Boolean = false
    ) {
        addBlockChange(
            pos.x,
            pos.y,
            pos.z,
            newMaterial,
            copyBlockData,
            updateBlock
        )
    }

    /**
     * Bulk add block changes - more efficient for large operations
     */
    fun addBlockChanges(changes: Collection<BlockChange>) {
        // Distribute changes across workers evenly
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
            if (!(worker.getMetrics()["isRunning"] as Boolean)) {
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
     */
    private fun rebalanceQueues() {
        configLock.lock()
        try {
            if (workers.isEmpty()) return

            // Calculate total and average work
            var totalWork = 0
            for (size in queueSizes) {
                totalWork += size.get()
            }

            if (totalWork < 100) return // Not worth rebalancing for small queues

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
        repeat(count) {
            val change = sourceQueue.poll() ?: return transferred
            targetQueue.add(change)
            transferred++
        }
        return transferred
    }
}