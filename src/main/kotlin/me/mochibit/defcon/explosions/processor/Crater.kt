package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.utils.BlockChange
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import org.bukkit.Location
import org.bukkit.Material
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.max

class Crater(
    val center: Location,
    private val radiusX: Int,
    private val radiusY: Int,
    private val radiusZ: Int,
    private val transformationRule: TransformationRule,
    private val destructionHeight: Int? = null
): Completable by CompletionDispatcher() {
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

    private val blockChanger = BlockChanger.getInstance(world)

    /**
     * Creates an ellipsoidal crater with the specified dimensions.
     * The crater has an inner part that is completely empty and an outer
     * ring that contains transformed blocks.
     */
    fun create() {
        try {
            // Clear any previous blocks
            processedBlocks.clear()

            // Using a volume-based approach for better coverage
            createVolumetricCrater()

            // Process any remaining blocks
            if (processedBlocks.isNotEmpty()) {
                applyBlockChanges(processedBlocks)
                processedBlocks.clear()
            }

            chunkCache.cleanupCache()

            complete()
        } catch (e: Exception) {
            // Log the error but prevent it from crashing the server
            println("Error in Crater creation: ${e.message}")
            e.printStackTrace()

            // Make sure to release the chunk cache even if an error occurs
            chunkCache.cleanupCache()
        }
    }

    /**
     * Creates the ellipsoidal crater using a volumetric processing approach.
     * Processes every block in the bounding box and determines if it's inside the ellipsoid.
     */
    private fun createVolumetricCrater() {
        // Create a bounding box around the ellipsoid
        for (x in -radiusX - 1..radiusX + 1) {
            val xPos = x + centerX

            for (z in -radiusZ - 1..radiusZ + 1) {
                val zPos = z + centerZ

                // Calculate normalized position for horizontal check - used for both crater and destruction height
                val normalizedX = (x * x).toDouble() / outerRadiusXSquared
                val normalizedZ = (z * z).toDouble() / outerRadiusZSquared
                val horizontalEllipsoidValue = normalizedX + normalizedZ

                // Skip if this x,z position is outside the horizontal projection of the ellipsoid
                if (horizontalEllipsoidValue > 1.0) continue

                // Process the crater part first (from bottom to centerY + radiusY)
                for (y in -radiusY - 1..radiusY + 1) {
                    val yPos = y + centerY

                    // Calculate normalized position within inner ellipsoid
                    val normalizedY = (y * y).toDouble() / innerRadiusYSquared
                    val normalizedInnerY = (y * y).toDouble() / outerRadiusYSquared

                    val innerEllipsoidValue = normalizedX * (innerRadiusXSquared.toDouble() / outerRadiusXSquared) +
                                             normalizedY +
                                             normalizedZ * (innerRadiusZSquared.toDouble() / outerRadiusZSquared)

                    val outerEllipsoidValue = normalizedX + normalizedInnerY + normalizedZ

                    // Skip if definitely outside our ellipsoid
                    if (outerEllipsoidValue > 1.0) continue

                    val type = chunkCache.getBlockMaterial(xPos, yPos, zPos)

                    // Skip air blocks and blacklisted materials
                    if (type.isAir || type in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                        continue
                    }

                    // Handle blocks based on whether they're in the inner or outer ellipsoid
                    if (innerEllipsoidValue <= 1.0) {
                        // Inside inner ellipsoid - completely empty
                        processedBlocks.add(
                            BlockChange(
                                xPos,
                                yPos,
                                zPos,
                                Material.AIR
                            )
                        )
                    } else {
                        // Skip liquids in the outer shell
                        if (type in TransformationRule.LIQUID_MATERIALS) continue

                        // Between inner and outer ellipsoid - transform blocks
                        // Calculate transformation intensity based on distance from center
                        val transformationIntensity = calculateTransformationIntensity(innerEllipsoidValue, outerEllipsoidValue)

                        processedBlocks.add(
                            BlockChange(
                                xPos,
                                yPos,
                                zPos,
                                transformationRule.transformMaterial(type, transformationIntensity),
                                updateBlock = true
                            )
                        )
                    }

                    // Process blocks in batches to reduce server load
                    if (processedBlocks.size >= batchSize) {
                        applyBlockChanges(processedBlocks)
                        processedBlocks.clear()
                    }
                }

                // Now process the destruction height column if specified
                // This will create a column of air from centerY up to destructionHeight
                if (destructionHeight != null) {
                    // Only process destruction height if the horizontal position is within the ellipsoid
                    if (horizontalEllipsoidValue <= 1.0) {
                        // Process from center Y (where the crater starts) up to the destruction height
                        for (y in centerY + 1..destructionHeight) {
                            val type = chunkCache.getBlockMaterial(xPos, y, zPos)

                            // Only replace non-air blocks
                            if (!type.isAir && type !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                                processedBlocks.add(
                                    BlockChange(
                                        xPos,
                                        y,
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
        }
    }

    /**
     * Calculates transformation intensity based on position between inner and outer ellipsoid
     */
    private fun calculateTransformationIntensity(innerValue: Double, outerValue: Double): Double {
        // Linear interpolation between inner (1.0) and outer (1.0) ellipsoids
        // This gives a value between 0.0 (inner edge) and 1.0 (outer edge)
        val range = 1.0 - innerValue
        return if (range <= 0) 1.0 else (outerValue - innerValue) / range
    }

    /**
     * Applies block changes in batch to reduce server load
     */
    private fun applyBlockChanges(blocks: List<BlockChange>) {
        blockChanger.addBlockChanges(blocks)
    }
}