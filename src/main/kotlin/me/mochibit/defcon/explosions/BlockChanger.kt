/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

import me.mochibit.defcon.threading.scheduling.intervalWithTask
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.*
import org.bukkit.metadata.MetadataValue
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object BlockChanger {
    data class BlockChange(
        val block: Block,
        val newMaterial: Material,
        val copyBlockData: Boolean,
        val metadataKey: String? = null,
        val metadataValue: MetadataValue? = null
    )

    private val queue: Queue<BlockChange> = ConcurrentLinkedQueue()
    private var running = false

    fun addBlockChange(
        block: Block,
        newMaterial: Material,
        copyBlockData: Boolean = false,
        metadataKey: String? = null,
        metadataValue: MetadataValue? = null
    ) {
        if (!running) start()
        queue.add(BlockChange(block, newMaterial, copyBlockData, metadataKey, metadataValue))
    }

    fun start() {
        if (running) return
        running = true

        intervalWithTask(1L, 0) { task ->
            var processedCount = 0

            while (processedCount < 5000 && queue.isNotEmpty()) {
                val blockChange = queue.poll()
                if (blockChange != null) {
                    applyBlockChange(blockChange)
                    processedCount++
                }
            }

            task.cancel()
            running = false
        }
    }

    private fun applyBlockChange(blockChange: BlockChange) {
        val block = blockChange.block

        if (blockChange.copyBlockData) {
            val oldBlockData = block.blockData.clone()
            block.type = blockChange.newMaterial
            val newBlockData = block.blockData

            when (oldBlockData) {
                is Directional -> {
                    (newBlockData as? Directional)?.facing = oldBlockData.facing
                }

                is Rail -> {
                    (newBlockData as? Rail)?.shape = oldBlockData.shape
                }

                is Bisected -> {
                    (newBlockData as? Bisected)?.half = oldBlockData.half
                }

                is Orientable -> {
                    (newBlockData as? Orientable)?.axis = oldBlockData.axis
                }

                is Rotatable -> {
                    (newBlockData as? Rotatable)?.rotation = oldBlockData.rotation
                }

                is Snowable -> {
                    (newBlockData as? Snowable)?.isSnowy = oldBlockData.isSnowy
                }

                is Waterlogged -> {
                    (newBlockData as? Waterlogged)?.isWaterlogged = oldBlockData.isWaterlogged
                }
            }

            block.blockData = newBlockData
        } else {
            block.type = blockChange.newMaterial
        }

        if (blockChange.metadataKey != null && blockChange.metadataValue != null) {
            block.setMetadata(blockChange.metadataKey, blockChange.metadataValue)
        }
    }

}
