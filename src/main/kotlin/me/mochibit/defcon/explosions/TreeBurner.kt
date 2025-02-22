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
import me.mochibit.defcon.extensions.toVector3f
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.utils.FloodFill3D.getFloodFillBlock
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.metadata.FixedMetadataValue
import org.joml.Vector3i

class TreeBurner(val world: World, private val center: Vector3i,  private val maxTreeBlocks: Int = 500, private val transformationRule: TransformationRule) {

    fun processTreeBurn(initialBlock: Block, normalizedExplosionPower: Double) {
        val leafSuffix = "_LEAVES"
        val logSuffix = "_LOG"
        val terrainTypes = setOf(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL)

        val treeBlocks = getFloodFillBlock(initialBlock, maxTreeBlocks, ignoreEmpty = true) { block ->
            block.type.name.endsWith(logSuffix) ||
            block.type.name.endsWith(leafSuffix) ||
            block.type in terrainTypes
        }

        // Classify blocks into categories in one pass
        val categorizedBlocks = mutableMapOf<String, MutableList<Location>>()
        for ((block, locations) in treeBlocks.entries) {
            when {
                block.name.endsWith(leafSuffix) -> categorizedBlocks.computeIfAbsent("LEAVES") { mutableListOf() }
                    .addAll(locations)

                block.name.endsWith(logSuffix) -> categorizedBlocks.computeIfAbsent("LOG") { mutableListOf() }
                    .addAll(locations)

                block in terrainTypes -> categorizedBlocks.computeIfAbsent("TERRAIN") { mutableListOf() }
                    .addAll(locations)
            }
        }

        // Process leaves
        categorizedBlocks["LEAVES"]?.let { leafBlocks ->
            for (leafBlockLocation in leafBlocks) {
                BlockChanger.addBlockChange(
                    leafBlockLocation.block,
                    Material.AIR,
                    metadataKey = "processedByTreeBurn",
                    metadataValue = FixedMetadataValue(Defcon.instance, true)
                )
            }
        }

        // Process logs with consistent tilt from base to top
        categorizedBlocks["LOG"]?.let { logBlocks ->
            val shockwaveDirection = initialBlock.location.subtract(center.x.toDouble(), center.y.toDouble(), center.z.toDouble()).toVector3f().normalize()


            val treeMinHeight = logBlocks.minOf { it.y }
            val treeMaxHeight = logBlocks.maxOf { it.y }
            val heightRange = treeMaxHeight - treeMinHeight

            for (logBlockLocation in logBlocks) {
                val logBlock = logBlockLocation.block
                if (normalizedExplosionPower > 0.5) {
                    BlockChanger.addBlockChange(logBlock, Material.AIR)
                    continue
                }

                val blockHeight = logBlockLocation.y - treeMinHeight
                val heightFactor = blockHeight / heightRange
                var tiltFactor = heightFactor * normalizedExplosionPower * 6 // Smooth gradient tilt

                if (logBlockLocation.y == treeMinHeight) {
                    tiltFactor = 0.0
                }

                val newPosition = logBlockLocation.add(
                    shockwaveDirection.x * tiltFactor,
                    0.0,
                    shockwaveDirection.z * tiltFactor
                )

                BlockChanger.addBlockChange(
                    newPosition.block,
                    Material.POLISHED_BASALT,
                    metadataKey = "processedByTreeBurn",
                    metadataValue = FixedMetadataValue(Defcon.instance, true)
                )
                if (newPosition.block.location != logBlock.location) {
                    BlockChanger.addBlockChange(logBlock, Material.AIR)
                }
            }
        }

        // Process terrain blocks
        categorizedBlocks["TERRAIN"]?.let { terrainBlocks ->
            for (terrainBlockLocation in terrainBlocks) {
                val block = terrainBlockLocation.block
                BlockChanger.addBlockChange(
                    block,
                    Material.COARSE_DIRT,
                    metadataKey = "processedByTreeBurn",
                    metadataValue = FixedMetadataValue(Defcon.instance, true)
                )
            }
        }
    }
}