package me.mochibit.defcon.content.explosion.processor.carver

import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.roundToInt
import kotlin.math.sqrt

sealed interface CraterPoint {
    val x: Int; val y: Int; val z: Int; val topY: Int; val normalizedDistance: Double

    data class Floor(
        override val x: Int, override val y: Int, override val z: Int,
        override val topY: Int, override val normalizedDistance: Double,
    ) : CraterPoint

    data class Rim(
        override val x: Int, override val y: Int, override val z: Int,
        override val topY: Int, override val normalizedDistance: Double,
        val debrisHeight: Int,
    ) : CraterPoint
}

object CraterCarver {

    data class Params(
        val centerX: Int, val centerY: Int, val centerZ: Int,
        val radiusX: Int, val radiusY: Int, val radiusZ: Int,
        val debrisRimWidth: Int,
        val collapseHeight: Int,
        val seaLevel: Int,
        val minBuildHeight: Int,
        val maxBuildHeight: Int,
    )

    private val scorchMaterials = listOf(
        Blocks.TUFF.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
        Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
        Blocks.COAL_BLOCK.defaultBlockState(), Blocks.BLACK_CONCRETE_POWDER.defaultBlockState(),
        Blocks.BLACK_CONCRETE.defaultBlockState(),
    )
    private val debrisMaterials = listOf(
        Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
        Blocks.STONE.defaultBlockState(),
    )

    fun carveColumn(dx: Int, dz: Int, params: Params, ctx: CraterCarveContext) {
        val point = calculateCraterPoint(dx, dz, params, ctx) ?: return
        when (point) {
            is CraterPoint.Rim -> processDebrisRim(point, params, ctx)
            is CraterPoint.Floor -> processCraterFloor(point, params, ctx)
        }
    }

    private fun findActualTerrainLevel(x: Int, z: Int, startY: Int, params: Params, ctx: CraterCarveContext): Int {
        for (y in startY downTo params.minBuildHeight) {
            val state = ctx.getState(x, y, z)
            if (state in MaterialCategories.TERRAIN_BLOCKS) return y
            if (state == Blocks.BEDROCK.defaultBlockState() || y < params.minBuildHeight + 5) return params.seaLevel
        }
        return params.seaLevel
    }

    private fun calculateCraterPoint(dx: Int, dz: Int, params: Params, ctx: CraterCarveContext): CraterPoint? {
        val x = params.centerX + dx
        val z = params.centerZ + dz
        val highestY = ctx.heightAt(x, z, Heightmap.Types.MOTION_BLOCKING)
        val terrainY = findActualTerrainLevel(x, z, highestY, params, ctx)

        val distSquared = dx * dx + dz * dz
        val maxRadiusSquared = maxOf(params.radiusX * params.radiusX, params.radiusZ * params.radiusZ)
        val normalizedDistance = sqrt(distSquared.toDouble() / maxRadiusSquared)

        val debrisRimStart = 1.0
        val debrisRimEnd = 1.0 + (params.debrisRimWidth.toDouble() / params.radiusX)
        if (normalizedDistance > debrisRimEnd) return null

        if (normalizedDistance > debrisRimStart) {
            val rimProgress = (normalizedDistance - debrisRimStart) / (debrisRimEnd - debrisRimStart)
            val maxRimHeight = (params.radiusY * 0.25).toInt().coerceAtLeast(2).coerceAtMost(6)
            val noise = wangNoise(x, 0, z)
            val noiseVariation = (noise - 0.5) * 0.5
            val baseRimHeight = (maxRimHeight * (1.0 - rimProgress * rimProgress)).toInt()
            val variedRimHeight = (baseRimHeight * (1.0 + noiseVariation)).toInt().coerceAtLeast(0)

            return CraterPoint.Rim(x, terrainY, z, highestY, normalizedDistance, variedRimHeight)
        }

        val heightAboveSeaLevel = maxOf(0, terrainY - params.seaLevel)
        val depthFactor = distSquared.toDouble() / maxRadiusSquared
        val baseDepth = (params.radiusY * (1.0 - sqrt(depthFactor))).toInt()
        val terrainFactor = (heightAboveSeaLevel.toDouble() / params.radiusY).coerceIn(0.0, 1.0)
        val adjustedDepth = (baseDepth * (0.8 + terrainFactor * 0.2)).toInt()

        val craterFloorY = terrainY - adjustedDepth
        val minFloorY = maxOf(params.minBuildHeight, params.seaLevel - params.radiusY)
        val finalY = craterFloorY.coerceAtLeast(minFloorY).coerceAtMost(params.maxBuildHeight - 1)

        return CraterPoint.Floor(x, finalY, z, highestY, normalizedDistance)
    }

    private fun processCraterFloor(point: CraterPoint.Floor, params: Params, ctx: CraterCarveContext) {
        val state = ctx.getState(point.x, point.y, point.z)
        if (state.canBeScorched()) {
            ctx.setState(point.x, point.y, point.z, selectScorchMaterial(point.normalizedDistance, point.x, point.z))
        }
        val maxClearHeight = minOf(point.topY + 4, params.centerY + params.collapseHeight, params.maxBuildHeight - 1)
        for (y in maxClearHeight downTo (point.y + 1)) {
            val s = ctx.getState(point.x, y, point.z)
            if (s.canBeRemoved() && !s.isAir) ctx.setState(point.x, y, point.z, Blocks.AIR.defaultBlockState())
        }
    }

    private fun processDebrisRim(point: CraterPoint.Rim, params: Params, ctx: CraterCarveContext) {
        val maxClearHeight = minOf(point.topY + 4, params.centerY + params.collapseHeight, params.maxBuildHeight - 1)
        for (y in maxClearHeight downTo (point.y + 1)) {
            val s = ctx.getState(point.x, y, point.z)
            if (s.canBeRemoved() && !s.isAir) ctx.setState(point.x, y, point.z, Blocks.AIR.defaultBlockState())
        }

        val targetHeight = point.y + point.debrisHeight
        for (y in point.y + 1..targetHeight) {
            val noise = wangNoise(point.x, y, point.z)
            val materialIndex = (noise * debrisMaterials.size).toInt().coerceIn(debrisMaterials.indices)
            ctx.setState(point.x, y, point.z, debrisMaterials[materialIndex])
        }
        if (point.debrisHeight > 0 && wangNoise(point.x, 0, point.z) > 0.4) {
            ctx.setState(point.x, targetHeight, point.z, scorchMaterials.take(3).random())
        }
    }

    private fun selectScorchMaterial(normalizedDistance: Double, x: Int, z: Int): BlockState {
        val clamped = normalizedDistance.coerceIn(0.0, 1.0)
        val distortion = (wangNoise(x, 0, z) - 0.5) * 0.25
        val finalDistance = (clamped + distortion).coerceIn(0.0, 1.0)
        val index = ((1.0 - finalDistance) * (scorchMaterials.size - 1)).roundToInt().coerceIn(scorchMaterials.indices)
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