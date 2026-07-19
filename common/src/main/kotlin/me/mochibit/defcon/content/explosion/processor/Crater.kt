package me.mochibit.defcon.content.explosion.processor

import kotlinx.coroutines.coroutineScope
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import me.mochibit.defcon.foundation.extension.awaitUnpaused
import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
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

    private val centerX = center.x
    private val centerY = center.y
    private val centerZ = center.z

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

    private data class CraterPoint(
        val x: Int,
        val y: Int,
        val z: Int,
        val topY: Int,
        val normalizedDistance: Double,
        val isDebrisRim: Boolean,
        val debrisHeight: Int,
    )

    private suspend fun findActualTerrainLevel(
        x: Int, z: Int, startY: Int, chunk: ChunkAccess,
    ): Int = coroutineScope {
        val mutablePos = BlockPos.MutableBlockPos()
        for (y in startY downTo level.minBuildHeight) {
            val blockState = chunk.getBlockState(mutablePos.set(x, y, z))

            if (blockState in MaterialCategories.TERRAIN_BLOCKS) {
                return@coroutineScope y
            }
            if (blockState == Blocks.BEDROCK.defaultBlockState() || y < level.minBuildHeight + 5) {
                return@coroutineScope seaLevel
            }
        }
        seaLevel
    }

    private suspend fun calculateCraterPoint(
        dx: Int, dz: Int, chunk: ChunkAccess,
    ): CraterPoint? = coroutineScope {
        val x = centerX + dx
        val z = centerZ + dz
        val highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        val terrainY = findActualTerrainLevel(x, z, highestY, chunk)

            val distSquared = dx * dx + dz * dz
            val maxRadiusSquared = maxOf(radiusX * radiusX, radiusZ * radiusZ)
            val normalizedDistance = sqrt(distSquared.toDouble() / maxRadiusSquared)

            val debrisRimStart = 1.0
            val debrisRimEnd = 1.0 + (debrisRimWidth.toDouble() / radiusX)

            if (normalizedDistance > debrisRimEnd) {
                return@coroutineScope null
            }

            if (normalizedDistance > debrisRimStart) {
                val rimProgress = (normalizedDistance - debrisRimStart) / (debrisRimEnd - debrisRimStart)
                val maxRimHeight = (radiusY * 0.25).toInt().coerceAtLeast(2).coerceAtMost(6)

                val noise = wangNoise(x, 0, z)
                val noiseVariation = (noise - 0.5) * 0.5

                val baseRimHeight = (maxRimHeight * (1.0 - rimProgress * rimProgress)).toInt()
                val variedRimHeight = (baseRimHeight * (1.0 + noiseVariation)).toInt().coerceAtLeast(0)

                return@coroutineScope CraterPoint(
                    x = x, y = terrainY, z = z,
                    normalizedDistance = normalizedDistance,
                    isDebrisRim = true,
                    debrisHeight = variedRimHeight,
                    topY = highestY
                )
            }

            val heightAboveSeaLevel = maxOf(0, terrainY - seaLevel)
            val depthFactor = distSquared.toDouble() / maxRadiusSquared
            val baseDepth = (radiusY * (1.0 - sqrt(depthFactor))).toInt()

            val terrainFactor = (heightAboveSeaLevel.toDouble() / radiusY.toDouble()).coerceIn(0.0, 1.0)
            val adjustedDepth = (baseDepth * (0.8 + terrainFactor * 0.2)).toInt()

            val craterFloorY = terrainY - adjustedDepth
            val minFloorY = maxOf(bounds.minY, seaLevel - radiusY)
            val finalY = craterFloorY.coerceAtLeast(minFloorY).coerceAtMost(level.maxBuildHeight - 1)

            CraterPoint(
                x = x, y = finalY, z = z,
                normalizedDistance = normalizedDistance,
                isDebrisRim = false,
                debrisHeight = 0,
                topY = highestY
            )
        }


    private suspend fun generateCrater() = coroutineScope {
        val maxRadius = radiusX + debrisRimWidth
        val maxNormSq = (1.0 + debrisRimWidth.toDouble() / radiusX).pow(2)

        val minChunkX = (centerX - maxRadius) shr 4
        val maxChunkX = (centerX + maxRadius) shr 4
        val minChunkZ = (centerZ - maxRadius) shr 4
        val maxChunkZ = (centerZ + maxRadius) shr 4

        var cellCounter = 0
        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val chunk = level.getChunk(cx, cz)

                val xRange = maxOf(cx shl 4, centerX - maxRadius)..minOf((cx shl 4) + 15, centerX + maxRadius)
                val zRange = maxOf(cz shl 4, centerZ - maxRadius)..minOf((cz shl 4) + 15, centerZ + maxRadius)

                for (x in xRange) for (z in zRange) {
                    cellCounter++
                    if (cellCounter % 64 == 0) level.awaitUnpaused()

                    val dx = x - centerX
                    val dz = z - centerZ
                    val normalizedDistance = (dx.toDouble() / radiusX).pow(2) + (dz.toDouble() / radiusZ).pow(2)
                    if (normalizedDistance > maxNormSq) continue

                    val point = calculateCraterPoint(dx, dz, chunk) ?: continue
                    if (point.isDebrisRim) processDebrisRim(point, chunk) else processCraterFloor(point, chunk)
                }
            }
        }
    }

    private suspend fun processCraterFloor(point: CraterPoint, chunk: ChunkAccess) {
        applyFloorScorching(point)
        clearToCraterFloor(point, chunk)
    }

    private suspend fun processDebrisRim(point: CraterPoint, chunk: ChunkAccess) =
        coroutineScope {
            val maxClearHeight = minOf(point.topY + 4, centerY + collapseHeight, level.maxBuildHeight - 1)
            val mutablePos = BlockPos.MutableBlockPos()

            for (y in maxClearHeight downTo (point.y + 1)) {
                val blockType = chunk.getBlockState(mutablePos.set(point.x, y, point.z))
                if (blockType.canBeRemoved() && !blockType.isAir) {
                    blockChanger.addBlockChange(point.x, y, point.z, Blocks.AIR.defaultBlockState(), updateBlock = false)
                }
            }

            val baseY = point.y
            val targetHeight = baseY + point.debrisHeight
            for (y in baseY + 1..targetHeight) {
                val noise = wangNoise(point.x, y, point.z)
                val materialIndex = ((noise * debrisMaterials.size).toInt()).coerceIn(debrisMaterials.indices)
                blockChanger.addBlockChange(point.x, y, point.z, debrisMaterials[materialIndex], updateBlock = false)
            }

            if (point.debrisHeight > 0 && wangNoise(point.x, 0, point.z) > 0.4) {
                val scorchMaterial = scorchMaterials.take(3).random()
                blockChanger.addBlockChange(point.x, targetHeight, point.z, scorchMaterial, updateBlock = false)
            }
        }

    private suspend fun applyFloorScorching(point: CraterPoint) =
        coroutineScope {
            val blockType = level.getBlockState(point.x, point.y, point.z)
            if (!blockType.canBeScorched()) return@coroutineScope

            val material = selectScorchMaterial(point.normalizedDistance, point.x, point.z)
            blockChanger.addBlockChange(point.x, point.y, point.z, material, updateBlock = false)
        }

    private suspend fun clearToCraterFloor(point: CraterPoint, chunk: ChunkAccess) = coroutineScope {
        val maxClearHeight = minOf(point.topY + 4, centerY + collapseHeight, level.maxBuildHeight - 1)
        val mutablePos = BlockPos.MutableBlockPos()

        for (y in maxClearHeight downTo (point.y + 1)) {
            val blockType = chunk.getBlockState(mutablePos.set(point.x, y, point.z))
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
        val noise = wangNoise(x, 0, z)
        val distortion = (noise - 0.5) * 0.25
        val finalDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        val index =
            ((1.0 - finalDistance) * (scorchMaterials.size - 1))
                .roundToInt()
                .coerceIn(scorchMaterials.indices)

        return scorchMaterials[index]
    }

    private fun BlockState.canBeScorched(): Boolean =
        !isAir && this !in MaterialCategories.LIQUID_MATERIALS && this !in MaterialCategories.INDESTRUCTIBLE_BLOCKS

    private fun BlockState.canBeRemoved(): Boolean = canBeScorched() || this in MaterialCategories.LIQUID_MATERIALS
}

private fun wangNoise(x: Int, y: Int, z: Int): Double {
    var hash = x.toLong() * 0x1f1f1f1f
    hash = hash xor (y.toLong() * 0x27d4eb2d)
    hash = hash xor (z.toLong() * 0x85ebca77)
    hash = hash xor (hash ushr 15)
    hash *= 0xc2b2ae3d
    hash = hash xor (hash ushr 16)
    return (hash and 0x7FFFFFFF) / 2147483647.0
}