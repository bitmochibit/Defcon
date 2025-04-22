package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.utils.BlockChange
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry
import org.bukkit.Location
import org.bukkit.Material
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

class Crater(
    val center: Location,
    private val radiusX: Int,
    private val radiusY: Int,
    private val radiusZ: Int,
    private val transformationRule: TransformationRule,
    private val destructionHeight: Int, // Made non-nullable as it's required for shock wave effect
    private val scorchRadius: Int = max(radiusX, radiusZ) + 5 // Extra radius for scorch marks
): Completable by CompletionDispatcher() {
    private val world = center.world
    private val chunkCache = ChunkCache.getInstance(world)
    private val centerX = center.x.roundToInt()
    private val centerY = center.y.roundToInt()
    private val centerZ = center.z.roundToInt()
    private val tolerance = 0.5

    // Calculate the maximum radius for iteration bounds
    private val maxRadius = maxOf(radiusX, radiusY, radiusZ, scorchRadius)

    // Pre-compute inner and outer radius squared values for each axis
    private val innerRadiusXSquared = ((radiusX - tolerance).pow(2)).toInt()
    private val innerRadiusYSquared = ((radiusY - tolerance).pow(2)).toInt()
    private val innerRadiusZSquared = ((radiusZ - tolerance).pow(2)).toInt()

    private val outerRadiusXSquared = ((radiusX + tolerance).pow(2)).toInt()
    private val outerRadiusYSquared = ((radiusY + tolerance).pow(2)).toInt()
    private val outerRadiusZSquared = ((radiusZ + tolerance).pow(2)).toInt()

    // Scorch radius squared (for ground effect calculations)
    private val scorchRadiusSquared = (scorchRadius * scorchRadius).toDouble()

    // Materials for scorch marks (from darkest to lightest)
    private val scorchMaterials = listOf(
        Material.BLACK_CONCRETE_POWDER,
        Material.BLACK_CONCRETE,
        Material.COAL_BLOCK,
        Material.BLACKSTONE,
        Material.BASALT,
        Material.DEEPSLATE,
        Material.TUFF
    )

    // Batch processing for block changes
    private val processedBlocks = mutableListOf<BlockChange>()
    private val batchSize = 500 // Adjust based on server performance

    // Track which blocks have been processed
    private val processedPositions = ConcurrentHashMap.newKeySet<Long>()

    private fun isBlockProcessed(x: Int, y: Int, z: Int): Boolean {
        return !processedPositions.add(Geometry.packIntegerCoordinates(x,y,z))
    }

    private val blockChanger = BlockChanger.getInstance(world)

    /**
     * Creates an ellipsoidal crater with the specified dimensions and applies
     * a shock wave effect that destroys blocks from the crater up to the destruction height.
     */
    fun create() {
        try {
            // Clear any previous data
            processedBlocks.clear()
            processedPositions.clear()

            // First pass: Create the complete destructive effect (crater + shock wave)
            createDestructiveEffect()

            // Second pass: Add scorch marks around the crater
            createScorchMarks()

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
     * Creates the complete destructive effect: crater + vertical shock wave
     */
    private fun createDestructiveEffect() {
        // First create a 2D horizontal map of the crater's intersection points at different heights
        val craterHeightMap = HashMap<Pair<Int, Int>, Int>()

        // Process the entire area
        for (x in -radiusX - 1..radiusX + 1) {
            val xPos = x + centerX

            for (z in -radiusZ - 1..radiusZ + 1) {
                val zPos = z + centerZ

                // Calculate normalized horizontal position
                val normalizedX = (x * x).toDouble() / outerRadiusXSquared
                val normalizedZ = (z * z).toDouble() / outerRadiusZSquared
                val horizontalEllipsoidValue = normalizedX + normalizedZ

                // Skip if this x,z position is outside the horizontal projection of the ellipsoid
                if (horizontalEllipsoidValue > 1.0) continue

                // Find the highest Y position of the crater at this x,z coordinate
                val craterTopY = findCraterTopY(x, z)

                // Store the height in our map
                craterHeightMap[Pair(xPos, zPos)] = craterTopY

                // Process blocks inside the crater ellipsoid
                processCraterEllipsoid(xPos, zPos, x, z)

                // Now process the vertical shock wave column from crater top to destruction height
                for (y in craterTopY + 1..destructionHeight) {
                    val yPos = y

                    // Skip if we've already processed this position
                    if (isBlockProcessed(xPos, yPos, zPos)) continue

                    val blockType = chunkCache.getBlockMaterial(xPos, yPos, zPos)

                    // Skip air blocks and blacklisted materials
                    if (!blockType.isAir && blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                        processedBlocks.add(
                            BlockChange(
                                xPos,
                                yPos,
                                zPos,
                                Material.AIR
                            )
                        )
                    }
                }

                // Process blocks in batches to reduce server load
                if (processedBlocks.size >= batchSize) {
                    applyBlockChanges(processedBlocks)
                    processedBlocks.clear()
                }
            }
        }
    }

    /**
     * Finds the highest Y position where the crater ellipsoid intersects with the given x,z coordinate
     */
    private fun findCraterTopY(x: Int, z: Int): Int {
        val normalizedX = (x * x).toDouble() / outerRadiusXSquared
        val normalizedZ = (z * z).toDouble() / outerRadiusZSquared

        // If we're at or beyond the edge of the ellipsoid horizontally, return centerY
        if (normalizedX + normalizedZ > 1.0) return centerY

        // Calculate how much "budget" we have for the Y component
        val remainingComponent = 1.0 - (normalizedX + normalizedZ)

        // Convert this to a Y value using the Y radius
        val yComponent = sqrt(remainingComponent * outerRadiusYSquared)

        return (centerY + yComponent).toInt()
    }

    /**
     * Process all blocks inside the crater ellipsoid at the given x,z coordinate
     */
    private fun processCraterEllipsoid(xPos: Int, zPos: Int, xOffset: Int, zOffset: Int) {
        // Calculate normalized position for horizontal check
        val normalizedX = (xOffset * xOffset).toDouble() / outerRadiusXSquared
        val normalizedZ = (zOffset * zOffset).toDouble() / outerRadiusZSquared

        // Find lowest and highest Y positions of the ellipsoid at this x,z
        val lowestY = max(0, centerY - radiusY)
        val highestY = min(world.maxHeight - 1, centerY + radiusY)

        // Process the entire Y column from lowest to highest
        for (y in lowestY..highestY) {
            // Calculate normalized Y position
            val yOffset = y - centerY
            val normalizedY = (yOffset * yOffset).toDouble() / outerRadiusYSquared

            // Calculate inner ellipsoid values
            val normalizedInnerY = (yOffset * yOffset).toDouble() / innerRadiusYSquared

            val innerEllipsoidValue = normalizedX * (innerRadiusXSquared.toDouble() / outerRadiusXSquared) +
                                     normalizedInnerY +
                                     normalizedZ * (innerRadiusZSquared.toDouble() / outerRadiusZSquared)

            val outerEllipsoidValue = normalizedX + normalizedY + normalizedZ

            // Skip if outside the ellipsoid
            if (outerEllipsoidValue > 1.0) continue

            // Skip if we've already processed this position
            if (isBlockProcessed(xPos, y, zPos)) continue

            // Get block type
            val blockType = chunkCache.getBlockMaterial(xPos, y, zPos)

            // Skip air blocks and blacklisted materials
            if (blockType.isAir || blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                continue
            }

            // Handle blocks based on whether they're in the inner or outer ellipsoid
            if (innerEllipsoidValue <= 1.0) {
                // Inside inner ellipsoid - completely empty
                processedBlocks.add(
                    BlockChange(
                        xPos,
                        y,
                        zPos,
                        Material.AIR
                    )
                )
            } else {
                // Skip liquids in the outer shell
                if (blockType in TransformationRule.LIQUID_MATERIALS) {
                    continue
                }

                // Between inner and outer ellipsoid - transform blocks
                // Calculate transformation intensity based on distance from center
                val transformationIntensity = calculateTransformationIntensity(innerEllipsoidValue, outerEllipsoidValue)

                processedBlocks.add(
                    BlockChange(
                        xPos,
                        y,
                        zPos,
                        transformationRule.transformMaterial(blockType, transformationIntensity),
                        updateBlock = true
                    )
                )
            }
        }
    }

    /**
     * Create scorch marks around the crater that gradually fade from black to grey
     */
    private fun createScorchMarks() {
        // Process a wider area for scorch marks
        for (x in -scorchRadius..scorchRadius) {
            val xPos = x + centerX

            for (z in -scorchRadius..scorchRadius) {
                val zPos = z + centerZ

                // Calculate distance from crater center (horizontal only)
                val distSquared = (x * x + z * z).toDouble()

                // Skip if outside scorch radius
                if (distSquared > scorchRadiusSquared) continue

                // Calculate normalized horizontal position for crater bounds check
                val normalizedX = (x * x).toDouble() / outerRadiusXSquared
                val normalizedZ = (z * z).toDouble() / outerRadiusZSquared
                val horizontalEllipsoidValue = normalizedX + normalizedZ

                // Skip if inside the crater
                if (horizontalEllipsoidValue <= 1.0) continue

                // Find highest non-air block (the ground surface)
                var surfaceY = -1
                for (y in destructionHeight downTo 0) {
                    // Skip if this position has been processed already
                    if (isBlockProcessed(xPos, y, zPos)) continue

                    if (!chunkCache.getBlockMaterial(xPos, y, zPos).isAir &&
                        chunkCache.getBlockMaterial(xPos, y + 1, zPos).isAir) {
                        surfaceY = y
                        break
                    }
                }

                // Skip if no surface found
                if (surfaceY == -1) continue

                // Get the surface block
                val surfaceBlock = chunkCache.getBlockMaterial(xPos, surfaceY, zPos)

                // Skip liquids and blacklisted blocks
                if (surfaceBlock in TransformationRule.LIQUID_MATERIALS ||
                    surfaceBlock in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                    continue
                }

                // Calculate intensity based on distance from crater edge
                // 0.0 = crater edge (darkest), 1.0 = scorch radius edge (lightest)
                val craterEdgeDist = sqrt(horizontalEllipsoidValue)
                val normalizedDist = sqrt(distSquared) / scorchRadius
                val scorchIntensity = (normalizedDist - craterEdgeDist) / (1.0 - min(craterEdgeDist, 1.0))

                // Select appropriate scorch material based on intensity
                val scorchMaterial = selectScorchMaterial(scorchIntensity)

                // Skip if already processed
                if (isBlockProcessed(xPos, surfaceY, zPos)) continue

                // Apply scorch mark
                processedBlocks.add(
                    BlockChange(
                        xPos,
                        surfaceY,
                        zPos,
                        scorchMaterial
                    )
                )

                // Process blocks in batches
                if (processedBlocks.size >= batchSize) {
                    applyBlockChanges(processedBlocks)
                    processedBlocks.clear()
                }
            }
        }
    }

    /**
     * Selects an appropriate scorch material based on intensity
     * 0.0 = darkest (closest to crater), 1.0 = lightest (furthest from crater)
     */
    private fun selectScorchMaterial(intensity: Double): Material {
        val clampedIntensity = intensity.coerceIn(0.0, 1.0)
        val index = min((clampedIntensity * scorchMaterials.size).toInt(), scorchMaterials.size - 1)
        return scorchMaterials[index]
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