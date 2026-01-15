package me.mochibit.defcon.explosions.processor

import kotlinx.coroutines.coroutineScope
import me.mochibit.defcon.transformer.material.MaterialCategories
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry.wangNoise
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Optimized crater generation with terrain-adaptive shaping and Kotlin idiomatic patterns.
 * Creates the bowl-shaped depression and scorched floor.
 * Above-ground destruction is handled by Shockwave for consistent behavior.
 */
class Crater(
    private val center: Location,
    private val radiusX: Int,
    private val radiusY: Int, // Depth for the paraboloid
    private val radiusZ: Int,
) {
    private val world = center.world ?: error("World cannot be null for crater generation")
    private val chunkCache by lazy { ChunkCache.getInstance(world) }
    private val blockChanger by lazy { BlockChanger.getInstance(world) }

    // Configuration for crater bounds
    private val centerX = center.blockX
    private val centerY = center.blockY
    private val centerZ = center.blockZ
    private val bounds =
        CraterBounds(
            minX = centerX - radiusX - 2,
            maxX = centerX + radiusX + 2,
            minZ = centerZ - radiusZ - 2,
            maxZ = centerZ + radiusZ + 2,
            minY = maxOf(centerY - radiusY, world.minHeight),
        )

    // Scorch materials (ordered from least to most intense)
    private val scorchMaterials =
        listOf(
            Material.TUFF,
            Material.DEEPSLATE,
            Material.BASALT,
            Material.BLACKSTONE,
            Material.COAL_BLOCK,
            Material.BLACK_CONCRETE_POWDER,
            Material.BLACK_CONCRETE,
        )

    private data class CraterBounds(
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
        val minY: Int,
    )

    suspend fun create() {
        generateCrater()
        blockChanger.flush()
        println("Crater creation completed")
    }

    /**
     * Represents a point in the crater floor with terrain-adaptive height
     */
    private data class CraterPoint(
        val x: Int,
        val y: Int,
        val z: Int,
        val normalizedDistance: Double,
    )

    /**
     * Calculate terrain-adaptive crater floor height
     */
    private fun calculateAdaptiveCraterFloor(
        dx: Int,
        dz: Int,
        terrainY: Int,
    ): CraterPoint {
        val x = centerX + dx
        val z = centerZ + dz

        // Distance from center (squared for efficiency)
        val distSquared = dx * dx + dz * dz
        val maxRadiusSquared = maxOf(radiusX * radiusX, radiusZ * radiusZ)
        val normalizedDistance = sqrt(distSquared.toDouble() / maxRadiusSquared)

        // Paraboloid crater shape (deeper in center)
        val depthFactor = distSquared.toDouble() / maxRadiusSquared
        val idealCraterFloorY = centerY - (radiusY * (1.0 - depthFactor)).toInt()

        // Edge blending - gradually blend with terrain near edges
        val edgeBlendStart = 0.8
        val blendFactor =
            when {
                depthFactor < edgeBlendStart -> {
                    0.7
                }

                // Strong crater shape in center
                else -> {
                    val edgeFactor = (depthFactor - edgeBlendStart) / (1.0 - edgeBlendStart)
                    0.7 * (1.0 - edgeFactor * 0.85) // Gradually blend to terrain
                }
            }

        // Blend crater floor with natural terrain
        val blendedY = (idealCraterFloorY * blendFactor + terrainY * (1.0 - blendFactor)).toInt()

        // Ensure crater floor stays within valid world bounds only
        // The blending formula already handles the relationship to terrain
        val finalY = blendedY.coerceAtLeast(bounds.minY).coerceAtMost(world.maxHeight - 1)

        return CraterPoint(x, finalY, z, normalizedDistance)
    }

    private fun generateCraterEffectivePlane(): Sequence<CraterPoint> =
        sequence {
            for (dx in -radiusX - 2..radiusX + 2) {
                for (dz in -radiusZ - 2..radiusZ + 2) {
                    // Check if point is within ellipse bounds
                    val normalizedDistance = (dx.toDouble() / radiusX).pow(2) + (dz.toDouble() / radiusZ).pow(2)
                    if (normalizedDistance > 1.0) continue

                    val x = centerX + dx
                    val z = centerZ + dz

                    // Get natural terrain height at this position
                    val terrainY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES)

                    yield(calculateAdaptiveCraterFloor(dx, dz, terrainY))
                }
            }
        }

    private suspend fun generateCrater() =
        coroutineScope {
            val points = generateCraterEffectivePlane().toList()

            // Process points concurrently in batches for better performance
            points.forEach { point ->
                processPoint(point)
            }
        }

    private suspend fun processPoint(point: CraterPoint) {
        applyFloorScorching(point)
        clearToCraterFloor(point)
    }

    private suspend fun applyFloorScorching(point: CraterPoint) {
        val blockType = chunkCache.getBlockMaterialAsync(point.x, point.y, point.z)
        if (!blockType.canBeScorched()) return

        val material = selectScorchMaterial(point.normalizedDistance, point.x, point.z)
        blockChanger.addBlockChange(point.x, point.y, point.z, material, updateBlock = false)
    }

    /**
     * Clears all blocks from surface down to crater floor, creating the bowl shape.
     * The crater floor remains solid - we only remove blocks ABOVE it.
     * Shockwave will then process from radius 0 with sophisticated destruction logic.
     */
    private suspend fun clearToCraterFloor(point: CraterPoint) {
        // Get the actual surface height at this position
        val surfaceY = world.getHighestBlockYAt(point.x, point.z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES)

        // Calculate a reasonable max height to clear (higher of surface or center + some height)
        val maxRemovalY = maxOf(surfaceY, centerY + radiusY).coerceAtMost(world.maxHeight - 1)

        // Clear blocks above crater floor (from max height down to just above the crater floor)
        // This creates the bowl shape without digging underneath
        for (y in maxRemovalY downTo (point.y + 1)) {
            val blockType = chunkCache.getBlockMaterialAsync(point.x, y, point.z)
            if (blockType.canBeRemoved()) {
                blockChanger.addBlockChange(point.x, y, point.z, Material.AIR, updateBlock = false)
            }
        }

        // Do NOT dig below the crater floor - it should remain solid
        // The scorched surface at point.y is the bottom of the crater
    }

    private fun selectScorchMaterial(
        normalizedDistance: Double,
        x: Int,
        z: Int,
    ): Material {
        val clampedDistance = normalizedDistance.coerceIn(0.0, 1.0)

        // Add procedural noise for natural variation
        val noise = wangNoise(x, 0, z)
        val distortion = (noise - 0.5) * 0.25
        val finalDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        // Select material based on intensity (closer to center = more intense)
        val index =
            ((1.0 - finalDistance) * (scorchMaterials.size - 1))
                .roundToInt()
                .coerceIn(scorchMaterials.indices)

        return scorchMaterials[index]
    }

    // Extension functions for more idiomatic Kotlin
    private fun Material.canBeScorched(): Boolean =
        !isAir && this !in MaterialCategories.LIQUID_MATERIALS && this !in MaterialCategories.INDESTRUCTIBLE_BLOCKS

    private fun Material.canBeRemoved(): Boolean = canBeScorched()
}
