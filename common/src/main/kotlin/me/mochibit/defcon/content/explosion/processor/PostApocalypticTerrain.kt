package me.mochibit.defcon.content.explosion.processor

import me.mochibit.defcon.content.explosion.BlastZone
import me.mochibit.defcon.content.explosion.processor.carver.ColumnCarver
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.explosion.processor.Shockwave
import net.minecraft.core.BlockPos
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.chunk.ChunkAccess
import kotlin.math.sqrt
import kotlin.random.Random

object PostApocalypticTerrain {
    private const val EDGE_FEATHER = 20.0
    private const val WOBBLE_FACTOR = 0.12
    fun maxFeatherMargin(radius: Int): Double = EDGE_FEATHER + radius * WOBBLE_FACTOR


    fun apply(level: WorldGenLevel, chunk: ChunkAccess, zone: BlastZone) {

        val chunkPos = chunk.pos
        val chunkSeed = chunkPos.toLong() xor 0x5DEECE66DL
        val transformer = MaterialTransformer(random = Random(chunkSeed))
        val seaLevelMinus3 = level.seaLevel - 3
        val worldMinHeight = chunk.minBuildHeight

        val chunkCtx = WorldGenColumnCarveContext(level, chunk, BlockPos(zone.centerX, zone.centerY, zone.centerZ))
        val effectiveShockwaveRange = (zone.radius - zone.radiusStart).toFloat()

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val worldX = chunkPos.minBlockX + x
                val worldZ = chunkPos.minBlockZ + z

                val dx = (worldX - zone.centerX).toDouble()
                val dz = (worldZ - zone.centerZ).toDouble()
                val dist = sqrt(dx * dx + dz * dz).toFloat()


                val distanceFromCraterEdge = dist - zone.radiusStart
                val radiusProgress = distanceFromCraterEdge / effectiveShockwaveRange
                val explosionPower = Shockwave.calculateShockwavePower(radiusProgress)

                var surfaceY = chunk.maxBuildHeight - 1
                val posMutable = BlockPos.MutableBlockPos(worldX, surfaceY, worldZ)
                while (surfaceY > chunk.minBuildHeight) {
                    posMutable.setY(surfaceY)
                    val state = chunk.getBlockState(posMutable)
                    if (!state.isAir) {
                        break
                    }
                    surfaceY--
                }
                val firstState = chunk.getBlockState(BlockPos(worldX, surfaceY, worldZ))

                val randomOffset = transformer.random.nextInt(1, 6)
                val convertToAirMinY = (level.seaLevel + randomOffset) +
                        (zone.shockwaveHeight * 0.5f * (1.0f - explosionPower)).toInt()

                ColumnCarver.carveColumn(
                    x = worldX, topY = surfaceY, z = worldZ,
                    power = explosionPower,
                    convertToAirMinY = convertToAirMinY,
                    worldMinHeight = worldMinHeight,
                    seaLevelMinus3 = seaLevelMinus3,
                    materialTransformer = transformer,
                    firstBlockState = firstState,
                    ctx = chunkCtx,
                )
            }
        }

    }

}