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

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.utils.BlockChange
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import org.bukkit.Location
import org.bukkit.Material
import kotlin.math.pow
import kotlin.math.roundToInt

class Crater(
    val center: Location,
    private val radiusX: Int,
    private val radiusY: Int,
    private val radiusZ: Int,
    private val transformationRule: TransformationRule,
    private val destructionHeight: Int? = null
) {
    private val world = center.world
    private val chunkCache = ChunkCache.getInstance(world)
    private val centerX = center.x.roundToInt()
    private val centerY = center.y.roundToInt()
    private val centerZ = center.z.roundToInt()
    private val tolerance = 0.5

    // Calculate the maximum radius for iteration bounds
    private val maxRadius = maxOf(radiusX, radiusY, radiusZ)

    // Pre-compute inner and outer radius squared values for each axis
    private val innerRadiusXSquared = ((radiusX - tolerance).pow(2)).toInt()
    private val innerRadiusYSquared = ((radiusY - tolerance).pow(2)).toInt()
    private val innerRadiusZSquared = ((radiusZ - tolerance).pow(2)).toInt()

    private val outerRadiusXSquared = ((radiusX + tolerance).pow(2)).toInt()
    private val outerRadiusYSquared = ((radiusY + tolerance).pow(2)).toInt()
    private val outerRadiusZSquared = ((radiusZ + tolerance).pow(2)).toInt()

    // Batch processing for block changes
    private val processedBlocks = mutableListOf<BlockChange>()
    private val batchSize = 500 // Adjust based on server performance

    private val blockChanger = BlockChanger(world)

    /**
     * Creates an ellipsoidal crater with the specified dimensions.
     * The crater has an inner part that is completely empty and an outer
     * ring that contains transformed blocks.
     */
    fun create() {
        try {
            // Clear any previous blocks
            processedBlocks.clear()

            // Use more efficient iteration approach
            createEllipsoidalCrater()

            // Handle destruction height if specified
            processDestructionHeight()

            // Process any remaining blocks
            if (processedBlocks.isNotEmpty()) {
                applyBlockChanges(processedBlocks)
                processedBlocks.clear()
            }

            // Important: Release the chunk cache when done
            chunkCache.cleanupCache()
        } catch (e: Exception) {
            // Log the error but prevent it from crashing the server
            println("Error in Crater creation: ${e.message}")
            e.printStackTrace()

            // Make sure to release the chunk cache even if an error occurs
            chunkCache.cleanupCache()
        }
    }

    /**
     * Creates the ellipsoidal crater using optimized bound checking
     */
    private fun createEllipsoidalCrater() {
        // Pre-calculate bounds for more efficient iteration
        val xBound = (radiusX + tolerance).toInt()
        val yBound = (radiusY + tolerance).toInt()
        val zBound = (radiusZ + tolerance).toInt()

        // Iterate through blocks in the bounding box
        for (x in -xBound..xBound) {
            for (y in -yBound..yBound) {
                // Early bound check for y-axis
                val normalizedY = (y * y).toDouble() / innerRadiusYSquared
                if (normalizedY > 1.2) continue

                for (z in -zBound..zBound) {
                    // Calculate normalized position within inner ellipsoid
                    val normalizedX = (x * x).toDouble() / innerRadiusXSquared
                    val normalizedZ = (z * z).toDouble() / innerRadiusZSquared

                    val innerEllipsoidValue = normalizedX + normalizedY + normalizedZ

                    // Skip blocks definitely outside our ellipsoid
                    if (innerEllipsoidValue > 1.2) {
                        continue
                    }

                    val xPos = x + centerX
                    val yPos = y + centerY
                    val zPos = z + centerZ

                    val type = chunkCache.getBlockMaterial(xPos, yPos, zPos)
                    when (type) {
                        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST -> continue
                        else -> Unit
                    }

                    // Only calculate outer ellipsoid values if needed
                    if (innerEllipsoidValue > 1.0) {
                        if (type in TransformationRule.LIQUID_MATERIALS) continue


                        // Calculate positions in outer ellipsoid
                        val normalizedOuterX = (x * x).toDouble() / outerRadiusXSquared
                        val normalizedOuterY = (y * y).toDouble() / outerRadiusYSquared
                        val normalizedOuterZ = (z * z).toDouble() / outerRadiusZSquared

                        val outerEllipsoidValue = normalizedOuterX + normalizedOuterY + normalizedOuterZ

                        // Skip if outside outer ellipsoid
                        if (outerEllipsoidValue > 1.0) continue

                        // Between inner and outer ellipsoid - transform blocks
                        processedBlocks.add(
                            BlockChange(
                                xPos,
                                yPos,
                                zPos,
                                transformationRule.transformMaterial(type, 1.0),
                                updateBlock = true
                            )
                        )
                    } else {
                        // Inside inner ellipsoid - completely empty
                        processedBlocks.add(
                            BlockChange(
                                xPos,
                                yPos,
                                zPos,
                                Material.AIR
                            )
                        )
                    }

                    // Process blocks in batches to reduce server load
                    if (processedBlocks.size >= batchSize) {
                        applyBlockChanges(processedBlocks)
                        processedBlocks.clear()
                    }
                }
            }
        }
    }

    /**
     * Processes the destruction height if specified
     */
    private fun processDestructionHeight() {
        destructionHeight?.let { height ->
            // Use more efficient bounds
            val xBound = (radiusX + tolerance).toInt()
            val zBound = (radiusZ + tolerance).toInt()

            for (x in -xBound..xBound) {
                for (z in -zBound..zBound) {
                    // Skip if outside the ellipsoid horizontal bounds
                    val normalizedX = (x * x).toDouble() / outerRadiusXSquared
                    val normalizedZ = (z * z).toDouble() / outerRadiusZSquared
                    if (normalizedX + normalizedZ > 1.0) continue

                    val xPos = x + centerX
                    val zPos = z + centerZ

                    // Destroy all blocks from center up to destruction height
                    for (y in centerY..height) {
                        val type = chunkCache.getBlockMaterial(xPos, y, zPos)
                        if (!type.isAir) {
                            processedBlocks.add(
                                BlockChange(
                                    xPos,
                                    y,
                                    zPos,
                                    Material.AIR
                                )
                            )
                        }

                        // Process blocks in batches
                        if (processedBlocks.size >= batchSize) {
                            applyBlockChanges(processedBlocks)
                            processedBlocks.clear()
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies block changes in batch to reduce server load
     */
    private fun applyBlockChanges(blocks: List<BlockChange>) {
        blockChanger.addBlockChanges(blocks)
    }
}