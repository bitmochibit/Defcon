package me.mochibit.defcon.explosion.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.TreeBurner
import me.mochibit.defcon.content.explosion.processor.core.ColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.core.ColumnCarver
import me.mochibit.defcon.content.explosion.processor.core.RuntimeColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.content.explosion.processor.worldgen.BlastZoneSavedData
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.random.Random


private object ShockwaveScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO + ServerCoroutineScope.coroutineContext
}

class Shockwave(
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun explode(): Job =
        ShockwaveScope.launch(Dispatchers.IO) {
            try {
                println("Shockwave starting from crater edge (radius $radiusStart) to $shockwaveRadius")
                val effectiveShockwaveRange = (shockwaveRadius - radiusStart).toFloat()


                var blocksProcessed = 0

                for (currentRadius in radiusStart..shockwaveRadius) {
                    if ((currentRadius - radiusStart) % 50 == 0) {
                        println("Processing shockwave radius: $currentRadius/$shockwaveRadius (blocks processed: $blocksProcessed)")
                    }

                    val distanceFromCraterEdge = (currentRadius - radiusStart).toFloat()
                    val radiusProgress = distanceFromCraterEdge / effectiveShockwaveRange
                    val power = calculateShockwavePower(radiusProgress)

                    generateShockwaveCircleBresenham(currentRadius)
                        .flowOn(Dispatchers.IO)
                        .collect { pos ->
                            val chunkX = pos.x shr 4
                            val chunkZ = pos.z shr 4

                            if (BlastZoneSavedData.get(level).isChunkWorldgenProcessed(chunkX, chunkZ)) return@collect
                            if (!level.hasChunk(chunkX, chunkZ)) return@collect
                            blocksProcessed++

                            val highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.x, pos.z)
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

    private val runtimeCtx = RuntimeColumnCarveContext(level, center)

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

    @Suppress("NOTHING_TO_INLINE")
    private inline fun generateTerrainNoise(
        x: Int,
        y: Int,
        z: Int,
        strength: Float,
    ): Float {
        val seed = ((x * 374761393L + y * 668265263L + z * 1274126177L) and 0x7FFFFFFF).toInt()
        val random = Random(seed)

        val noise1 = (random.nextDouble() - 0.5).toFloat()
        val noise2 = ((random.nextDouble() - 0.5) * 0.5).toFloat()
        val noise3 = ((random.nextDouble() - 0.5) * 0.25).toFloat()

        return ((noise1 + noise2 + noise3) * 1.143f * strength).coerceIn(-1.0f, 1.0f)
    }

    private suspend fun isHeuristicallyWallBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val east = level.getBlockState(x + 1, y, z).isAir
        val west = level.getBlockState(x - 1, y, z).isAir
        if (east && west) return true

        val south = level.getBlockState(x, y, z + 1).isAir
        if ((east || west) && south) return true

        val north = level.getBlockState(x, y, z - 1).isAir
        return ((east || west) && north) || (south && north)
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

    private suspend fun cleanup() {
        println("Shockwave completed")
    }
}