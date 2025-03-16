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
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.bukkit.scheduler.BukkitTask
import org.joml.Vector3i
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Lightweight position representation to avoid full Block references
 */
data class BlockPos(val x: Int, val y: Int, val z: Int)

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

    /**
     * Start processing the queue
     */
    fun start() {
        if (running) return
        running = true

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val startTime = System.nanoTime()
            var processedCount = 0

            while (processedCount < blocksPerTick && !queue.isEmpty()) {
                val blockChange = queue.poll() ?: break
                applyBlockChange(blockChange)
                queueSize.decrementAndGet()
                processedCount++
            }

            totalProcessed.addAndGet(processedCount)

            if (queue.isEmpty()) {
                stop()
            }
        }, tickInterval, tickInterval)
    }

    /**
     * Stop processing
     */
    fun stop() {
        if (!running) return
        running = false
        task?.cancel()
        task = null
    }

    /**
     * Apply a block change efficiently
     */
    private fun applyBlockChange(change: BlockChange) {
        val block = world.getBlockAt(change.x, change.y, change.z)

        // Skip if already the right material
        if (block.type == change.newMaterial) return

        // Capture block data before changing material if needed
        val oldBlockData = if (change.copyBlockData) block.blockData else null

        // Change block material
        block.setType(change.newMaterial, change.updateBlock)

        // Apply block data if needed
        if (change.copyBlockData && oldBlockData != null) {
            try {
                val newBlockData = block.blockData
                copyRelevantBlockData(oldBlockData, newBlockData)
                block.setBlockData(newBlockData, change.updateBlock)
            } catch (e: Exception) {
                // Silently fail if block data can't be copied
            }
        }
    }

    /**
     * Copy only the relevant block data properties
     */
    private fun copyRelevantBlockData(oldBlockData: BlockData, newBlockData: BlockData) {
        // Only copy properties that exist in both block data types
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
        // Add more block data types as needed
    }

    /**
     * Get performance metrics
     */
    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "queueSize" to queueSize.get(),
            "totalProcessed" to totalProcessed.get(),
            "isRunning" to running
        )
    }
}

/**
 * Optimized BlockChanger that manages block changes for a specific world
 */
class BlockChanger(private val world: World) {
    // Worker pool configuration
    private var workerCount = max(1, Runtime.getRuntime().availableProcessors() / 2)
    private var blocksPerWorkerTick = 1000
    private var tickInterval = 1L

    // Worker pool and distribution
    private val workers = ArrayList<BlockChangeWorker>()
    private val queues = ArrayList<ConcurrentLinkedQueue<BlockChange>>()
    private val queueSizes = ArrayList<AtomicInteger>()
    private var nextWorkerIndex = 0

    init {
        initializeWorkers()
    }

    /**
     * Initialize workers based on current configuration
     */
    private fun initializeWorkers() {
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
            workers.add(worker)
        }
    }

    /**
     * Configure the processing parameters
     */
    fun configure(
        workerCount: Int = max(1, Runtime.getRuntime().availableProcessors() / 2),
        blocksPerWorkerTick: Int = 1000,
        tickInterval: Long = 1L
    ) {
        this.workerCount = workerCount
        this.blocksPerWorkerTick = blocksPerWorkerTick
        this.tickInterval = tickInterval

        // Reinitialize with new configuration
        initializeWorkers()
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
     * Stop all workers
     */
    fun stopAll() {
        for (worker in workers) {
            worker.stop()
        }
    }

    /**
     * Check how many block changes are pending
     */
    fun getPendingChangesCount(): Int {
        var total = 0
        for (size in queueSizes) {
            total += size.get()
        }
        return total
    }

    /**
     * Get queue distribution for debugging
     */
    fun getQueueDistribution(): List<Int> {
        return queueSizes.map { it.get() }
    }

    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): Map<String, Any> {
        val workerMetrics = workers.mapIndexed { index, worker -> "worker$index" to worker.getMetrics() }.toMap()

        return mapOf(
            "workerCount" to workerCount,
            "blocksPerWorkerTick" to blocksPerWorkerTick,
            "pendingChanges" to getPendingChangesCount(),
            "queueDistribution" to getQueueDistribution(),
            "workers" to workerMetrics
        )
    }
}

/**
 * Factory for creating BlockChanger instances for different worlds
 */
object BlockChangerFactory {
    private val worldChangers = HashMap<String, BlockChanger>()

    /**
     * Get or create a BlockChanger for a specific world
     */
    fun getBlockChanger(world: World): BlockChanger {
        return worldChangers.getOrPut(world.name) {
            BlockChanger(world)
        }
    }

    /**
     * Configure all block changers
     */
    fun configureAll(
        workerCount: Int = max(1, Runtime.getRuntime().availableProcessors() / 2),
        blocksPerWorkerTick: Int = 1000,
        tickInterval: Long = 1L
    ) {
        for (changer in worldChangers.values) {
            changer.configure(workerCount, blocksPerWorkerTick, tickInterval)
        }
    }
}