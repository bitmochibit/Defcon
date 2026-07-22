package me.mochibit.defcon.explosion.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.mochibit.defcon.content.explosion.processor.carver.ColumnCarver
import me.mochibit.defcon.content.explosion.processor.carver.RuntimeColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.content.explosion.BlastZoneSavedData
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.extension.awaitUnpaused
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


private object ShockwaveScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO + ServerCoroutineScope.coroutineContext
}

class Shockwave(
    val explosionId: UUID,
    private val level: ServerLevel,
    private val center: BlockPos,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val materialTransformer: MaterialTransformer = MaterialTransformer(),
) {
    companion object {
        /**
         * Function describing the decay rate of the shockwave power
         */
        fun calculateShockwavePower(radiusProgress: Float): Float =
            when {
                radiusProgress < 0.4f -> {
                    1.0f - (radiusProgress / 0.4f) * 0.05f
                }

                radiusProgress < 0.7f -> {
                    val transitionProgress = (radiusProgress - 0.4f) / 0.3f
                    0.95f - (transitionProgress * 0.45f)
                }

                else -> {
                    val falloffProgress = (radiusProgress - 0.7f) / 0.3f
                    0.5f * (1.0f - falloffProgress.pow(2.0f))
                }
            }.coerceIn(0.0f, 1.0f)
    }

    private val centerX = center.x
    private val centerZ = center.z

    private val worldSeaLevel = level.seaLevel
    private val worldMinHeight = level.minBuildHeight
    private val worldMaxHeight = level.maxBuildHeight
    private val seaLevelMinus3 = worldSeaLevel - 3

    private val runtimeCtx = RuntimeColumnCarveContext(level, center)


    fun explode(): Job =
        ShockwaveScope.launch(Dispatchers.IO) {
            try {
                val effectiveShockwaveRange = (shockwaveRadius - radiusStart).toFloat()


                var blocksProcessed = 0

                for (currentRadius in radiusStart..shockwaveRadius) {
                    val distanceFromCraterEdge = (currentRadius - radiusStart).toFloat()
                    val radiusProgress = distanceFromCraterEdge / effectiveShockwaveRange
                    val power = calculateShockwavePower(radiusProgress)

                    generateShockwaveCircleBresenham(currentRadius)
                        .flowOn(Dispatchers.IO)
                        .collect { pos ->
                            level.awaitUnpaused()
                            val chunkX = pos.x shr 4
                            val chunkZ = pos.z shr 4

                            if (BlastZoneSavedData.get(level).isChunkWorldgenProcessed(explosionId, chunkX, chunkZ)) return@collect
                            if (!level.chunkSource.isPositionTicking(ChunkPos.asLong(pos))) return@collect
                            blocksProcessed++

                            val highestY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.x, pos.z)
                            val loc = BlockPos(pos.x, highestY, pos.z)
                            processBlock(loc, power, level.getBlockState(loc))
                        }

                }
            } catch (e: Exception) {
                println("ERROR in Shockwave: ${e.message}")
                e.printStackTrace()
            } finally {
                cleanup()
            }
        }


    private fun processBlock(
        blockLocation: BlockPos,
        power: Float, // power: 1.0 = max destruction (crater edge), 0.0 = min destruction (far from center)
        firstBlockState: BlockState,
    ) {
        val x = blockLocation.x
        val y = blockLocation.y
        val z = blockLocation.z

        val randomOffset = Random.nextInt(1, 6)
        val convertToAirMinY = (worldSeaLevel + randomOffset) + (shockwaveHeight * 0.5f * (1.0f - power)).toInt()

        ColumnCarver.carveColumn(
            x = x, topY = y, z = z,
            power = power,
            convertToAirMinY = convertToAirMinY,
            worldMinHeight = worldMinHeight,
            seaLevelMinus3 = seaLevelMinus3,
            materialTransformer = materialTransformer,
            firstBlockState = firstBlockState,
            ctx = runtimeCtx,
        )
    }

    private fun generateShockwaveCircleBresenham(radius: Int): Flow<BlockPos> =
        flow {
            if (radius == 0) {
                emit(BlockPos(centerX, worldMaxHeight, centerZ))
                return@flow
            }

            val radiusSquared = radius * radius
            val threshold = radius * 2 + 1
            val innerBound = maxOf(0, radiusSquared - threshold)

            val emitted = mutableSetOf<Long>()

            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val distSq = dx * dx + dz * dz

                    if (distSq > innerBound && distSq <= radiusSquared) {
                        val wx = centerX + dx
                        val wz = centerZ + dz
                        val key = (wx.toLong() shl 32) or (wz.toLong() and 0xFFFFFFFFL)
                        if (emitted.add(key)) {
                            emit(BlockPos(wx, worldMaxHeight, wz))
                        }
                    }
                }
            }
        }

    private fun cleanup() {
        println("Shockwave completed")
    }
}