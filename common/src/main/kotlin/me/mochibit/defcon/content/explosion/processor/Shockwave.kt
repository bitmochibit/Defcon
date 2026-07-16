package me.mochibit.defcon.explosion.processor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.TreeBurner
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

    // Services
    private val treeBurner = TreeBurner(level, center)
    private val blockChanger = BlockChanger.getInstance(level)

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

                            val highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.x, pos.z) - 1
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

    private suspend fun processBlock(
        blockLocation: BlockPos,
        power: Float, // power: 1.0 = max destruction (crater edge), 0.0 = min destruction (far from center)
        firstBlockState: BlockState,
    ) {
        val x = blockLocation.x
        val y = blockLocation.y
        val z = blockLocation.z

        val blockPosForLight = BlockPos.MutableBlockPos(x, y, z)
        val blockPosForTrees = BlockPos.MutableBlockPos(x, y, z)

        val randomOffset = Random.nextInt(1, 6)
        val convertToAirMinY = (worldSeaLevel + randomOffset) + (shockwaveHeight * 0.5f * (1.0f - power)).toInt()

        val terrainNoiseStrength = 0.3f + power * 0.4f
        val baseTerrainBreakChance = 0.7f + power * 0.25f
        val skylightThreshold = ((1.0f - power) * 12).toInt().coerceIn(2, 15)

        var consecutiveTerrainBlocks = 0
        var trunkTopY: Int? = null
        var trunkBaseY: Int? = null
        var consecutiveAirBlocks = 0
        var consecutiveFluids = 0
        var consecutiveBlacklisted = 0

        for (currentY in y downTo maxOf(seaLevelMinus3, worldMinHeight)) {
            if (treeBurner.isPosProcessed(x, currentY, z)) continue

            val currentState = if (currentY == y) firstBlockState else level.getBlockState(x, currentY, z)

            blockPosForTrees.set(x, currentY, z)
            if (treeBurner.isTreeBlock(blockPosForTrees)) {
                val block = currentState.block
                if (trunkTopY == null && (block in TreeBurnCore.LOG_BLOCKS || block in TreeBurnCore.WOOD_BLOCKS)) {
                    trunkTopY = currentY
                    trunkBaseY = treeBurner.findLocalTrunkBase(BlockPos(x, currentY, z))
                }
                treeBurner.processTreeBlockAt(BlockPos(x, currentY, z), power.toDouble(), trunkTopY, trunkBaseY)
                consecutiveTerrainBlocks = 0
                continue
            }


            when {
                currentState in MaterialCategories.INDESTRUCTIBLE_BLOCKS -> {
                    if (++consecutiveBlacklisted >= 2) break
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    continue
                }

                !currentState.fluidState.isEmpty -> {
                    if (++consecutiveFluids >= 2) break
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    continue
                }

                currentState.isAir -> {
                    if (++consecutiveAirBlocks >= 10) break
                    consecutiveTerrainBlocks = 0
                    continue
                }

                else -> {
                    consecutiveBlacklisted = 0
                    consecutiveFluids = 0
                    consecutiveAirBlocks = 0
                }
            }

            val isTerrainBlock = currentState in MaterialCategories.TERRAIN_BLOCKS
            val shouldConvertToAir = currentY > convertToAirMinY

            if (isHeuristicallyWallBlock(x, currentY, z)) {
                consecutiveTerrainBlocks = 0
                blockPosForLight.set(x, currentY, z)
                val skylightLevel = level.getBrightness(LightLayer.SKY, blockPosForLight)

                when {
                    currentY > seaLevelMinus3 -> {
                        blockChanger.addBlockChange(
                            x,
                            currentY,
                            z,
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            updateBlock = false
                        )
                    }

                    skylightLevel >= skylightThreshold -> {
                        if (Random.nextDouble() > 0.3) {
                            blockChanger.addBlockChange(
                                x,
                                currentY,
                                z,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                updateBlock = false
                            )
                        } else {
                            val lightInfluence = skylightLevel * 0.02f
                            val transformedBlock =
                                materialTransformer.transformMaterial(
                                    currentState,
                                    1.0f - power + lightInfluence, x, currentY, z
                                )
                            blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                        }
                    }

                    else -> {
                        val lightInfluence = skylightLevel * 0.01f
                        val transformedBlock =
                            materialTransformer.transformMaterial(
                                currentState,
                                1.0f - power + lightInfluence,
                                x, currentY, z
                            )
                        blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                    }
                }
                continue
            }

            if (isTerrainBlock) {
                if (++consecutiveTerrainBlocks >= 3) break

                val heightFactor = (currentY - seaLevelMinus3).toFloat() / (y - seaLevelMinus3).coerceAtLeast(1)
                val noiseValue = generateTerrainNoise(x, currentY, z, terrainNoiseStrength)

                if (consecutiveTerrainBlocks == 1) {
                    val finalBreakChance = baseTerrainBreakChance + noiseValue - (heightFactor * 0.15f)
                    val shouldBreakTerrain =
                        shouldConvertToAir ||
                                (Random.nextDouble() < finalBreakChance && currentY > seaLevelMinus3)

                    if (shouldBreakTerrain) {
                        blockChanger.addBlockChange(
                            x,
                            currentY,
                            z,
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            updateBlock = false
                        )
                        val adjacentNoise = generateTerrainNoise(x, currentY - 1, z, terrainNoiseStrength * 0.5f)
                        if (adjacentNoise > 0.15f) {
                            val belowState = level.getBlockState(x, currentY - 1, z)
                            if (belowState in MaterialCategories.TERRAIN_BLOCKS) {
                                blockChanger.addBlockChange(
                                    x,
                                    currentY - 1,
                                    z,
                                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                    updateBlock = false
                                )
                            }
                        }
                        continue
                    }
                }

                blockPosForLight.set(x, currentY, z)
                val skylightLevel = level.getBrightness(LightLayer.SKY, blockPosForLight)
                val noiseInfluence = noiseValue * 0.3f
                val lightInfluence = skylightLevel * 0.0133f
                val transformedBlock =
                    materialTransformer.transformMaterial(
                        currentState,
                        1.0f - power + noiseInfluence + lightInfluence, x, currentY, z
                    )
                blockChanger.addBlockChange(x, currentY, z, transformedBlock)

                val aboveState = level.getBlockState(x, currentY + 1, z)
                blockPosForTrees.set(x,currentY+1, z)
                if (!aboveState.isAir && !treeBurner.isTreeBlock(blockPosForTrees)) {
                    blockPosForLight.set(x, currentY + 1, z)
                    val aboveSkylightLevel = level.getBrightness(LightLayer.SKY, blockPosForLight)
                    val aboveNoise = generateTerrainNoise(x, currentY + 1, z, terrainNoiseStrength * 0.7f)
                    val aboveLightInfluence = aboveSkylightLevel * 0.01f
                    val transformedAbove =
                        materialTransformer.transformMaterial(
                            aboveState,
                            1.0f - power + (aboveNoise * 0.2f) + aboveLightInfluence,
                            x, currentY+1, z
                        )
                    blockChanger.addBlockChange(x, currentY + 1, z, transformedAbove)
                }
            } else {
                consecutiveTerrainBlocks = 0

                if (shouldConvertToAir) {
                    blockChanger.addBlockChange(
                        x,
                        currentY,
                        z,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        updateBlock = false
                    )
                } else {
                    blockPosForLight.set(x, currentY, z)
                    val skylightLevel = level.getBrightness(LightLayer.SKY, blockPosForLight)
                    val blockNoise = generateTerrainNoise(x, currentY, z, terrainNoiseStrength * 0.5f)
                    val lightInfluence = skylightLevel * 0.00667f
                    val transformedBlock =
                        materialTransformer.transformMaterial(
                            currentState,
                            1.0f - power + (blockNoise * 0.2f) + lightInfluence,
                            x, currentY, z
                        )
                    blockChanger.addBlockChange(x, currentY, z, transformedBlock, updateBlock = false)
                }
            }
        }
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