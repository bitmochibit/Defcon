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

package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.collection.Vector3iSetFull
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector2f
import org.joml.Vector3i

class TreeBurner(
    private val world: World,
    private val center: Vector3i,
    val distanceRatioCompletelyDestroy: Double = 0.1,
) {
    companion object {
        private const val LEAF_SUFFIX = "_LEAVES"
        private const val LOG_SUFFIX = "_LOG"
        private const val WOOD_SUFFIX = "_WOOD"
        private const val MAX_TREE_HEIGHT = 60

        // Pre-build sets for O(1) lookup
        private val TREE_BLOCKS =
            buildSet {
                Material.entries.forEach { material ->
                    with(material.name) {
                        when {
                            endsWith(LEAF_SUFFIX) || endsWith(LOG_SUFFIX) || endsWith(WOOD_SUFFIX) -> add(material)
                        }
                    }
                }
            }

        private val LEAF_BLOCKS = TREE_BLOCKS.filterTo(mutableSetOf()) { it.name.endsWith(LEAF_SUFFIX) }
        private val LOG_BLOCKS = TREE_BLOCKS.filterTo(mutableSetOf()) { it.name.endsWith(LOG_SUFFIX) }
        private val WOOD_BLOCKS = TREE_BLOCKS.filterTo(mutableSetOf()) { it.name.endsWith(WOOD_SUFFIX) }

        // Cache burnt replacements for faster lookup
        private val BURNT_REPLACEMENTS =
            buildMap {
                TREE_BLOCKS.forEach { material ->
                    put(
                        material,
                        when {
                            material in LEAF_BLOCKS -> Material.AIR
                            material.name.contains("WARPED") || material.name.contains("CRIMSON") -> Material.BLACKSTONE
                            else -> Material.POLISHED_BASALT
                        },
                    )
                }
            }
    }

    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChanger.getInstance(world)

    private val processedTreeBlocks = Vector3iSetFull()

    fun isPosProcessed(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean =
        processedTreeBlocks.contains(x, y, z).also {
            processedTreeBlocks.remove(x, y, z)
        }

    suspend fun processTreeBurn(
        initialBlock: Vector3i,
        explosionPower: Double,
    ) {
        val material = chunkCache.getBlockMaterialAsync(initialBlock.x, initialBlock.y, initialBlock.z)

        // Early exit checks
        if (!isTreeBlock(initialBlock)) {
            return
        }

        if (processedTreeBlocks.contains(initialBlock.x, initialBlock.y, initialBlock.z)) {
            return
        }

        try {
            val treeMaxHeight = initialBlock.y
            val treeMinHeight = findTreeBase(initialBlock)
            val effectiveMaxHeight = minOf(treeMinHeight + MAX_TREE_HEIGHT, treeMaxHeight)
            val heightRange = (effectiveMaxHeight - treeMinHeight).coerceAtLeast(1)

            var blocksProcessed = 0

            // Pre-compute shockwave direction
            val dx = (initialBlock.x - center.x).toFloat()
            val dz = (initialBlock.z - center.z).toFloat()
            val invMag = 1.0f / kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            val shockwaveDirection = Vector2f(dx * invMag, dz * invMag)

            val currentX = initialBlock.x
            val currentZ = initialBlock.z

            // Process vertical column from top to bottom
            for (y in effectiveMaxHeight downTo treeMinHeight) {
                val material = chunkCache.getBlockMaterialAsync(currentX, y, currentZ)

                // Skip non-tree blocks
                if (material !in TREE_BLOCKS) continue

                blocksProcessed++

                when {
                    material in LEAF_BLOCKS -> {
                        blockChanger.addBlockChange(currentX, y, currentZ, Material.AIR, updateBlock = true)
                        processedTreeBlocks.add(currentX, y, currentZ)
                    }

                    material in LOG_BLOCKS || material in WOOD_BLOCKS -> {
                        processWoodBlock(
                            currentX,
                            y,
                            currentZ,
                            material,
                            treeMinHeight,
                            heightRange,
                            shockwaveDirection,
                            explosionPower,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun findTreeBase(startBlock: Vector3i): Int {
        val minY = maxOf(0, startBlock.y - MAX_TREE_HEIGHT)
        val currentX = startBlock.x
        val currentZ = startBlock.z
        var currentY = startBlock.y

        // Descend until we hit terrain or non-tree block
        while (currentY > minY) {
            val material = chunkCache.getBlockMaterialAsync(currentX, currentY, currentZ)

            when {
                material == Material.AIR -> currentY--
                material !in TREE_BLOCKS -> return currentY + 1
                else -> currentY--
            }
        }

        return minY
    }

    suspend fun getTreeTerrain(startLoc: Vector3i): Vector3i = Vector3i(startLoc.x, findTreeBase(startLoc) - 1, startLoc.z)

    private suspend fun isTreeBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = chunkCache.getBlockMaterialAsync(x, y, z) in TREE_BLOCKS

    suspend fun isTreeBlock(block: Vector3i): Boolean = isTreeBlock(block.x, block.y, block.z)

    fun isTreeBlock(material: Material): Boolean = material in TREE_BLOCKS

    /**
     * Processes a wood block during tree destruction with tilting and burning.
     */
    private suspend fun processWoodBlock(
        x: Int,
        y: Int,
        z: Int,
        originalMaterial: Material,
        treeMinHeight: Int,
        heightRange: Int,
        shockwaveDirection: Vector2f,
        explosionPower: Double, // 0.0 = far from explosion, 1.0 = close to explosion
    ) {
        // Complete destruction for blocks very close to explosion (high power)
        if (explosionPower >= (1.0 - distanceRatioCompletelyDestroy)) {
            blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = true)
            processedTreeBlocks.add(x, y, z)
            return
        }

        // Get burnt material from cache
        val burntMaterial = BURNT_REPLACEMENTS[originalMaterial] ?: Material.POLISHED_BASALT

        // Calculate tilt intensity
        val tiltFactor = calculateTiltFactor(y, treeMinHeight, heightRange, explosionPower)

        if (tiltFactor > 0.0) {
            val newX = x + (shockwaveDirection.x * tiltFactor).toInt()
            val newZ = z + (shockwaveDirection.y * tiltFactor).toInt()
            tiltBlock(x, y, z, newX, newZ, burntMaterial)
        } else {
            blockChanger.addBlockChange(x, y, z, burntMaterial, updateBlock = true)
            processedTreeBlocks.add(x, y, z)
        }
    }

    /**
     * Calculates how much a block should tilt based on its height and distance from explosion.
     * Higher explosionPower = closer to explosion = MORE tilt (stronger force)
     * Lower explosionPower = farther from explosion = LESS tilt (weaker force)
     * Inline for better performance.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun calculateTiltFactor(
        blockY: Int,
        treeMinHeight: Int,
        heightRange: Int,
        explosionPower: Double,
    ): Double {
        if (blockY == treeMinHeight) return 0.0
        val heightFactor = (blockY - treeMinHeight).toDouble() / heightRange
        // Higher power (close to explosion) = stronger tilt force
        return heightFactor * explosionPower * 6.0
    }

    /**
     * Moves a block from original position to new tilted position
     */
    private suspend fun tiltBlock(
        originalX: Int,
        originalY: Int,
        originalZ: Int,
        newX: Int,
        newZ: Int,
        material: Material,
    ) {
        // Only move if position actually changed
        if (newX != originalX || newZ != originalZ) {
            blockChanger.addBlockChange(originalX, originalY, originalZ, Material.AIR, updateBlock = true)
            blockChanger.addBlockChange(newX, originalY, newZ, material, updateBlock = true)
            processedTreeBlocks.add(newX, originalY, originalZ)
        } else {
            // No movement, just change material
            blockChanger.addBlockChange(originalX, originalY, originalZ, material, updateBlock = true)
            processedTreeBlocks.add(originalX, originalY, originalZ)
        }
    }
}
