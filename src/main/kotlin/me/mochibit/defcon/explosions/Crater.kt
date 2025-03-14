package me.mochibit.defcon.explosions

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
    private val transformationRule: TransformationRule
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

    /**
     * Creates an ellipsoidal crater with the specified dimensions.
     * The crater has an inner part that is completely empty and an outer
     * ring that contains transformed blocks.
     */
    fun create() {
        // Process chunks in batches for better performance
        val processedBlocks = mutableListOf<Pair<Location, Material>>()
        val batchSize = 500 // Adjust based on server performance

        for (x in -maxRadius..maxRadius) {
            for (y in -maxRadius..maxRadius) {
                for (z in -maxRadius..maxRadius) {
                    // Calculate normalized position within ellipsoid
                    val normalizedX = (x * x).toDouble() / innerRadiusXSquared
                    val normalizedY = (y * y).toDouble() / innerRadiusYSquared
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
                    if (type.isAir) {
                        continue
                    }

                    // Calculate positions in outer ellipsoid
                    val normalizedOuterX = (x * x).toDouble() / outerRadiusXSquared
                    val normalizedOuterY = (y * y).toDouble() / outerRadiusYSquared
                    val normalizedOuterZ = (z * z).toDouble() / outerRadiusZSquared

                    val outerEllipsoidValue = normalizedOuterX + normalizedOuterY + normalizedOuterZ

                    val block = world.getBlockAt(xPos, yPos, zPos)

                    if (innerEllipsoidValue <= 1.0) {
                        // Inside inner ellipsoid - completely empty
                        processedBlocks.add(Pair(block.location, Material.AIR))
                    } else if (outerEllipsoidValue <= 1.0) {
                        // Between inner and outer ellipsoid - transform blocks
                        processedBlocks.add(Pair(
                            block.location,
                            transformationRule.transformMaterial(type, 1.0)
                        ))
                    }

                    // Process blocks in batches to reduce server load
                    if (processedBlocks.size >= batchSize) {
                        applyBlockChanges(processedBlocks)
                        processedBlocks.clear()
                    }
                }
            }
        }

        // Process any remaining blocks
        if (processedBlocks.isNotEmpty()) {
            applyBlockChanges(processedBlocks)
        }
    }

    /**
     * Applies block changes in batch to reduce server load
     */
    private fun applyBlockChanges(blocks: List<Pair<Location, Material>>) {
        for ((location, material) in blocks) {
            val block = world.getBlockAt(location)
            val updateBlock = material != Material.AIR // Only update physics for non-air blocks
            BlockChanger.addBlockChange(block, material, updateBlock)
        }
    }
}