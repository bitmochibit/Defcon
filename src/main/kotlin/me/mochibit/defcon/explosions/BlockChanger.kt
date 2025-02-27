package me.mochibit.defcon.explosions

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.threading.scheduling.intervalWithTask
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.bukkit.metadata.MetadataValue
import java.util.concurrent.ConcurrentLinkedQueue
import org.bukkit.plugin.Plugin

object BlockChanger {
    data class BlockChange(
        val block: Block,
        val newMaterial: Material,
        val copyBlockData: Boolean,
        val metadataKey: String? = null,
        val metadataValue: MetadataValue? = null,
        val blockData: BlockData? = null
    )

    private val queue = ConcurrentLinkedQueue<BlockChange>()
    private var running = false
    private var taskId = -1

    // Configurable parameters
    private var blocksPerTick = 1000
    private var tickInterval = 1L

    /**
     * Configure the processing parameters
     */
    fun configure(blocksPerTick: Int = 1000, tickInterval: Long = 1L) {
        this.blocksPerTick = blocksPerTick
        this.tickInterval = tickInterval

        // Restart the processor if running
        if (running) {
            stop(Defcon.instance)
            start(Defcon.instance)
        }
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
        queue.add(BlockChange(block, newMaterial, copyBlockData, metadataKey, metadataValue, blockData))
        if (!running) start(Defcon.instance)
    }

    /**
     * Start the block processing task
     */
    fun start(plugin: Plugin) {
        if (running) return
        running = true

        var emptyInterval: Long = 0

        taskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, {
            var processedCount = 0

            while (processedCount < blocksPerTick && queue.isNotEmpty()) {
                val blockChange = queue.poll() ?: break
                applyBlockChange(blockChange)
                processedCount++
            }

            // Auto-stop when queue is empty
            if (queue.isEmpty()) {
                emptyInterval += tickInterval
                if (emptyInterval >= tickInterval*40) {
                    running = false
                    plugin.server.scheduler.cancelTask(taskId)
                    taskId = -1
                }
            }

        }, tickInterval, tickInterval)
    }

    /**
     * Forcibly stop the processing task
     */
    fun stop(plugin: Plugin) {
        if (!running) return
        running = false
        if (taskId != -1) {
            plugin.server.scheduler.cancelTask(taskId)
            taskId = -1
        }
    }

    /**
     * Apply a block change efficiently based on the block type
     */
    private fun applyBlockChange(blockChange: BlockChange) {
        val block = blockChange.block
        val oldBlockData = blockChange.blockData ?: (if (blockChange.copyBlockData) block.blockData.clone() else null)

        // Change the block type
        block.type = blockChange.newMaterial

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
        return queue.size
    }
}