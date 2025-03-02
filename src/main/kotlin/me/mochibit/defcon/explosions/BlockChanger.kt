package me.mochibit.defcon.explosions

import me.mochibit.defcon.Defcon
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.bukkit.metadata.MetadataValue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicIntegerArray

object BlockChanger {
    data class BlockChange(
        val block: Block,
        val newMaterial: Material,
        val copyBlockData: Boolean,
        val metadataKey: String? = null,
        val metadataValue: MetadataValue? = null,
        val blockData: BlockData? = null
    )

    private var queues = mutableListOf<ConcurrentLinkedQueue<BlockChange>>()
    private var queueSizes = AtomicIntegerArray(1)
    private var taskIds = mutableListOf<Int>()
    private var running = false

    // Configurable parameters
    private var blocksPerTick = 1000
    private var tickInterval = 1L
    private var processorCount: Int = 6

    /**
     * Configure the processing parameters
     */
    fun configure(blocksPerTick: Int = 1000, tickInterval: Long = 1L, processorCount: Int = 6) {
        this.blocksPerTick = blocksPerTick
        this.tickInterval = tickInterval
        this.processorCount = processorCount

        queues.clear()
        queueSizes = AtomicIntegerArray(processorCount)

        repeat(processorCount) {
            queues.add(ConcurrentLinkedQueue<BlockChange>())
        }

        // Restart the processor if running
        if (running) {
            stop()
            start()
        }
    }

    private fun findBestQueue(): Int {
        var minIdx = 0
        var minSize = queueSizes.get(0)

        for (i in 1 until processorCount) {
            val size = queueSizes.get(i)
            if (size < minSize) {
                minIdx = i
                minSize = size
            }
        }

        return minIdx
    }

    /**
     * Add a block change to the processing queue
     */
    fun addBlockChange(
        block: Block,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        metadataKey: String? = null,
        metadataValue: MetadataValue? = null,
        blockData: BlockData? = null
    ) {
        val queueIdx = findBestQueue()
        val blockChange = BlockChange(block, newMaterial, copyBlockData, metadataKey, metadataValue, blockData)

        queues[queueIdx].add(blockChange)
        queueSizes.incrementAndGet(queueIdx)

        if (!running) start()
    }

    /**
     * Start the block processing task
     */
    private fun start() {
        if (running) return
        running = true
        taskIds.clear()

        for (queueIdx in 0 until processorCount) {
            val taskId = Defcon.instance.server.scheduler.scheduleSyncRepeatingTask(Defcon.instance, {
                var processedCount = 0
                val queue = queues[queueIdx]

                while (processedCount < blocksPerTick && queue.isNotEmpty()) {
                    val blockChange = queue.poll() ?: break
                    applyBlockChange(blockChange)
                    queueSizes.decrementAndGet(queueIdx)
                    processedCount++
                }

                if (queueAllEmpty()) {
                    stop()
                }
            }, tickInterval, tickInterval)
            taskIds.add(taskId)
        }

    }

    private fun queueAllEmpty(): Boolean {
        for (i in 0 until processorCount) {
            if (queueSizes.get(i) > 0) return false
        }
        return true
    }

    /**
     * Forcibly stop the processing task
     */
    private fun stop() {
        if (!running) return
        running = false

        taskIds.forEach { taskId ->
            Defcon.instance.server.scheduler.cancelTask(taskId)
        }
        taskIds.clear()
    }

    /**
     * Apply a block change efficiently based on the block type
     */
    private fun applyBlockChange(blockChange: BlockChange) {
        val block = blockChange.block
        val oldBlockData = blockChange.blockData ?: (if (blockChange.copyBlockData) block.blockData.clone() else null)

        // Change the block type
        block.setType(blockChange.newMaterial, false)

        // Apply block data if needed
        if (blockChange.copyBlockData && oldBlockData != null) {
            val newBlockData = block.blockData

            // Only check types that are relevant for this material
            // Apply only the properties that exist in both the old and new block data
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

            block.blockData = newBlockData
        }

        // Apply metadata if provided
        if (blockChange.metadataKey != null && blockChange.metadataValue != null) {
            block.setMetadata(blockChange.metadataKey, blockChange.metadataValue)
        }
    }

    /**
     * Check how many block changes are pending
     */
    fun getPendingChangesCount(): Int {
        var total = 0
        for (i in 0 until processorCount) {
            total += queueSizes.get(i)
        }
        return total
    }

    fun getQueueDistribution(): List<Int> { // Debugging
        return (0 until processorCount).map{ queueSizes.get(it) }
    }
}