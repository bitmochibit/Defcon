package me.mochibit.defcon.content.explosion.processor

import kotlinx.coroutines.coroutineScope
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Crater(
    private val level: ServerLevel,
    private val center: BlockPos,
    private val radiusX: Int,
    private val radiusY: Int,
    private val radiusZ: Int,
    val collapseHeight: Int = 200,
) {
    private val blockChanger by lazy { BlockChanger.getInstance(level) }

    // Configuration for crater bounds
    private val centerX = center.x
    private val centerY = center.y
    private val centerZ = center.z

    // Use sea level as anchor point
    private val seaLevel = level.seaLevel
    val debrisRimWidth = (radiusX * 0.3).toInt().coerceAtLeast(5)

    private val bounds =
        CraterBounds(
            minX = centerX - radiusX - debrisRimWidth,
            maxX = centerX + radiusX + debrisRimWidth,
            minZ = centerZ - radiusZ - debrisRimWidth,
            maxZ = centerZ + radiusZ + debrisRimWidth,
            minY = maxOf(centerY - radiusY, level.minBuildHeight),
        )

    // Scorch materials (ordered from least to most intense)
    private val scorchMaterials =
        listOf(
            Blocks.TUFF.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.BASALT.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(),
            Blocks.COAL_BLOCK.defaultBlockState(),
            Blocks.BLACK_CONCRETE_POWDER.defaultBlockState(),
            Blocks.BLACK_CONCRETE.defaultBlockState(),
        )

    // Debris materials for the raised rim
    private val debrisMaterials =
        listOf(
            Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
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
     * Represents a point in the crater with natural paraboloid shape
     */
    private data class CraterPoint(
        val x: Int,
        val y: Int,
        val z: Int,
        val normalizedDistance: Double,
        val isDebrisRim: Boolean,
        val debrisHeight: Int,
    )

    /**
     * Find the actual terrain level by scanning downward from a starting point.
     * Ignores structures and finds the first real terrain block.
     */
    private suspend fun findActualTerrainLevel(
        x: Int,
        z: Int,
        startY: Int,
    ): Int =
        coroutineScope {
            // Start from the given Y and scan downward
            for (y in startY downTo level.minBuildHeight) {
                val blockState = level.getBlockState(x, y, z)

                if (blockState in MaterialCategories.TERRAIN_BLOCKS) {
                    return@coroutineScope y
                }

                if (blockState == Blocks.BEDROCK.defaultBlockState() || y < level.minBuildHeight + 5) {
                    return@coroutineScope seaLevel
                }
            }

            // Fallback to sea level if nothing found
            seaLevel
        }

    /**
     * Calculate natural paraboloid crater floor height based on actual terrain.
     * Finds real ground level, ignoring structures above it.
     */
    private suspend fun calculateCraterPoint(
        dx: Int,
        dz: Int,
    ): CraterPoint? =
        coroutineScope {
            val x = centerX + dx
            val z = centerZ + dz

            // Get highest block (might be a structure)
            val highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)

            // Find actual terrain by scanning down from highest point
            val terrainY = findActualTerrainLevel(x, z, highestY)

            // Distance from center
            val distSquared = dx * dx + dz * dz
            val maxRadiusSquared = maxOf(radiusX * radiusX, radiusZ * radiusZ)
            val normalizedDistance = sqrt(distSquared.toDouble() / maxRadiusSquared)

            // Check if we're in debris rim zone (beyond crater edge but within rim)
            val debrisRimStart = 1.0
            val debrisRimEnd = 1.0 + (debrisRimWidth.toDouble() / radiusX)

            if (normalizedDistance > debrisRimEnd) {
                return@coroutineScope null // Outside crater influence
            }

            if (normalizedDistance > debrisRimStart) {
                // We're in the debris rim zone - calculate raised terrain
                val rimProgress = (normalizedDistance - debrisRimStart) / (debrisRimEnd - debrisRimStart)

                // Increased rim height for more visible debris
                val maxRimHeight = (radiusY * 0.25).toInt().coerceAtLeast(2).coerceAtMost(6)

                // Apply noise for natural variation
                val noise = wangNoise(x, 0, z)
                val noiseVariation = (noise - 0.5) * 0.5

                // Calculate rim elevation: starts high at crater edge, tapers to 0 at outer edge
                val baseRimHeight = (maxRimHeight * (1.0 - rimProgress * rimProgress)).toInt()
                val variedRimHeight = (baseRimHeight * (1.0 + noiseVariation)).toInt().coerceAtLeast(0)

                return@coroutineScope CraterPoint(
                    x = x,
                    y = terrainY,
                    z = z,
                    normalizedDistance = normalizedDistance,
                    isDebrisRim = true,
                    debrisHeight = variedRimHeight,
                )
            }

            // We're inside the crater - calculate depression from terrain
            val heightAboveSeaLevel = maxOf(0, terrainY - seaLevel)

            // Paraboloid crater shape (deeper in center, shallower at edges)
            val depthFactor = distSquared.toDouble() / maxRadiusSquared

            // Base crater depth: full radiusY at center, tapering to 0 at edges
            val baseDepth = (radiusY * (1.0 - sqrt(depthFactor))).toInt()

            // Adjust depth based on terrain height above sea level
            // Higher terrain = slightly deeper crater for more natural look
            val terrainFactor = (heightAboveSeaLevel.toDouble() / radiusY.toDouble()).coerceIn(0.0, 1.0)
            val adjustedDepth = (baseDepth * (0.8 + terrainFactor * 0.2)).toInt()

            // Calculate final crater floor
            val craterFloorY = terrainY - adjustedDepth

            // Ensure crater floor stays within valid bounds
            val minFloorY = maxOf(bounds.minY, seaLevel - radiusY)
            val finalY = craterFloorY.coerceAtLeast(minFloorY).coerceAtMost(level.maxBuildHeight - 1)

            CraterPoint(
                x = x,
                y = finalY,
                z = z,
                normalizedDistance = normalizedDistance,
                isDebrisRim = false,
                debrisHeight = 0,
            )
        }

    private suspend fun generateCraterEffectivePlane(): List<CraterPoint> =
        coroutineScope {
            val points = mutableListOf<CraterPoint>()

            val maxRadius = radiusX + debrisRimWidth

            for (dx in -maxRadius..maxRadius) {
                for (dz in -maxRadius..maxRadius) {
                    val normalizedDistance = (dx.toDouble() / radiusX).pow(2) + (dz.toDouble() / radiusZ).pow(2)

                    // Include both crater and debris rim
                    if (normalizedDistance <= (1.0 + debrisRimWidth.toDouble() / radiusX).pow(2)) {
                        val point = calculateCraterPoint(dx, dz)
                        if (point != null) {
                            points.add(point)
                        }
                    }
                }
            }

            points
        }

    private suspend fun generateCrater() =
        coroutineScope {
            val points = generateCraterEffectivePlane()

            points.forEach { point ->
                if (point.isDebrisRim) {
                    processDebrisRim(point)
                } else {
                    processCraterFloor(point)
                }
            }
        }

    private suspend fun processCraterFloor(point: CraterPoint) {
        applyFloorScorching(point)
        clearToCraterFloor(point)
    }

    private suspend fun processDebrisRim(point: CraterPoint) =
        coroutineScope {
            // First, clear any structures above terrain level
            val maxClearHeight = (centerY + collapseHeight).coerceAtMost(level.maxBuildHeight - 1)
            for (y in maxClearHeight downTo (point.y + 1)) {
                val blockType = level.getBlockState(point.x, y, point.z)
                if (blockType.canBeRemoved() && !blockType.isAir) {
                    blockChanger.addBlockChange(point.x, y, point.z, Blocks.AIR.defaultBlockState(), updateBlock = false)
                }
            }

            // Build up debris rim above the terrain
            val baseY = point.y
            val targetHeight = baseY + point.debrisHeight

            // Add debris blocks
            for (y in baseY + 1..targetHeight) {
                // Select debris material with variation
                val noise = wangNoise(point.x, y, point.z)
                val materialIndex = ((noise * debrisMaterials.size).toInt()).coerceIn(debrisMaterials.indices)
                val debrisMaterial = debrisMaterials[materialIndex]

                blockChanger.addBlockChange(point.x, y, point.z, debrisMaterial, updateBlock = false)
            }

            // Scorch the top of the debris rim
            if (point.debrisHeight > 0) {
                if (wangNoise(point.x, 0, point.z) > 0.4) {
                    val scorchMaterial = scorchMaterials.take(3).random()
                    blockChanger.addBlockChange(point.x, targetHeight, point.z, scorchMaterial, updateBlock = false)
                }
            }
        }

    private suspend fun applyFloorScorching(point: CraterPoint) =
        coroutineScope {
            val blockType = level.getBlockState(point.x, point.y, point.z)
            if (!blockType.canBeScorched()) return@coroutineScope

            val material = selectScorchMaterial(point.normalizedDistance, point.x, point.z)
            blockChanger.addBlockChange(point.x, point.y, point.z, material, updateBlock = false)
        }

    /**
     * Clears all blocks from collapse height down to crater floor.
     * This punches through any structures.
     */
    private suspend fun clearToCraterFloor(point: CraterPoint) =
        coroutineScope {
            val maxClearHeight = (centerY + collapseHeight).coerceAtMost(level.maxBuildHeight - 1)

            for (y in maxClearHeight downTo (point.y + 1)) {
                val blockType = level.getBlockState(point.x, y, point.z)
                if (blockType.canBeRemoved() && !blockType.isAir) {
                    blockChanger.addBlockChange(point.x, y, point.z, Blocks.AIR.defaultBlockState(), updateBlock = false)
                }
            }
        }

    private fun selectScorchMaterial(
        normalizedDistance: Double,
        x: Int,
        z: Int,
    ): BlockState {
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
    private fun BlockState.canBeScorched(): Boolean =
        !isAir && this !in MaterialCategories.LIQUID_MATERIALS && this !in MaterialCategories.INDESTRUCTIBLE_BLOCKS

    private fun BlockState.canBeRemoved(): Boolean = canBeScorched()
}


private fun wangNoise(x: Int, y: Int, z: Int): Double {
    // Wang hash for better pseudo-random distribution
    var hash = x.toLong() * 0x1f1f1f1f
    hash = hash xor (y.toLong() * 0x27d4eb2d)
    hash = hash xor (z.toLong() * 0x85ebca77)
    hash = hash xor (hash ushr 15)
    hash *= 0xc2b2ae3d
    hash = hash xor (hash ushr 16)
    return (hash and 0x7FFFFFFF) / 2147483647.0
}