package me.mochibit.defcon.content.explosion.processor.carver

import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.foundation.info
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import kotlin.random.Random

object ColumnCarver {
    fun carveColumn(
        x: Int,
        topY: Int,
        z: Int,
        power: Float,
        convertToAirMinY: Int,
        worldMinHeight: Int,
        seaLevelMinus3: Int,
        materialTransformer: MaterialTransformer,
        firstBlockState: BlockState,
        ctx: ColumnCarveContext,
    ) {
        val terrainNoiseStrength = 0.3f + power * 0.4f
        val baseTerrainBreakChance = 0.7f + power * 0.25f
        val skylightThreshold = ((1.0f - power) * 12).toInt().coerceIn(2, 15)

        var consecutiveTerrainBlocks = 0
        var trunkTopY: Int? = null
        var trunkBaseY: Int? = null
        var consecutiveAirBlocks = 0
        var consecutiveFluids = 0
        var consecutiveBlacklisted = 0

        for (currentY in topY downTo maxOf(seaLevelMinus3, worldMinHeight)) {
            if (ctx.isPosAlreadyBurnedByTree(x, currentY, z)) {
                continue
            }

            val currentState = if (currentY == topY) firstBlockState else ctx.getState(x, currentY, z)

            if (ctx.isTreeBlock(x, currentY, z)) {
                val block = currentState.block
                if (trunkTopY == null && (block in TreeBurnCore.LOG_BLOCKS || block in TreeBurnCore.WOOD_BLOCKS)) {
                    trunkTopY = currentY
                    trunkBaseY = ctx.findLocalTrunkBase(x, currentY, z)
                }
                ctx.burnTreeBlock(x, currentY, z, power.toDouble(), trunkTopY, trunkBaseY)
                consecutiveTerrainBlocks = 0
                consecutiveAirBlocks = 0
                consecutiveFluids = 0
                consecutiveBlacklisted = 0
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
                    if (++consecutiveAirBlocks >= 20) break
                    consecutiveTerrainBlocks = 0
                    consecutiveFluids = 0
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

            if (isWallLike(x, currentY, z, ctx)) {
                consecutiveTerrainBlocks = 0
                val skylightLevel = ctx.skylightAt(x, currentY, z)

                when {
                    currentY > seaLevelMinus3 -> {
                        ctx.setState(
                            x,
                            currentY,
                            z,
                            Blocks.AIR.defaultBlockState(),
                            updateBlock = false
                        )
                    }

                    skylightLevel >= skylightThreshold -> {
                        if (Random.nextDouble() > 0.3) {
                            ctx.setState(
                                x,
                                currentY,
                                z,
                                Blocks.AIR.defaultBlockState(),
                                updateBlock = false
                            )
                        } else {
                            val lightInfluence = skylightLevel * 0.02f
                            val transformedBlock =
                                materialTransformer.transformMaterial(
                                    currentState,
                                    1.0f - power + lightInfluence, x, currentY, z
                                )
                            ctx.setState(x, currentY, z, transformedBlock)
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
                        ctx.setState(x, currentY, z, transformedBlock)
                    }
                }
                continue
            }

            if (isTerrainBlock) {
                if (++consecutiveTerrainBlocks >= 3) break

                val heightFactor = (currentY - seaLevelMinus3).toFloat() / (topY - seaLevelMinus3).coerceAtLeast(1)
                val noiseValue = generateTerrainNoise(x, currentY, z, terrainNoiseStrength)

                if (consecutiveTerrainBlocks == 1) {
                    val finalBreakChance = baseTerrainBreakChance + noiseValue - (heightFactor * 0.15f)
                    val shouldBreakTerrain =
                        shouldConvertToAir ||
                                (Random.nextDouble() < finalBreakChance && currentY > seaLevelMinus3)

                    if (shouldBreakTerrain) {
                        ctx.setState(
                            x,
                            currentY,
                            z,
                            Blocks.AIR.defaultBlockState(),
                            updateBlock = false
                        )
                        val adjacentNoise = generateTerrainNoise(x, currentY - 1, z, terrainNoiseStrength * 0.5f)
                        if (adjacentNoise > 0.15f) {
                            val belowState = ctx.getState(x, currentY - 1, z)
                            if (belowState in MaterialCategories.TERRAIN_BLOCKS) {
                                ctx.setState(
                                    x,
                                    currentY - 1,
                                    z,
                                    Blocks.AIR.defaultBlockState(),
                                    updateBlock = false
                                )
                            }
                        }
                        continue
                    }
                }


                val skylightLevel = ctx.skylightAt(x, currentY, z)
                val noiseInfluence = noiseValue * 0.3f
                val lightInfluence = skylightLevel * 0.0133f
                val transformedBlock =
                    materialTransformer.transformMaterial(
                        currentState,
                        1.0f - power + noiseInfluence + lightInfluence, x, currentY, z
                    )
                ctx.setState(x, currentY, z, transformedBlock)

                val aboveState = ctx.getState(x, currentY + 1, z)
                if (!aboveState.isAir && !ctx.isTreeBlock(x, currentY + 1, z)) {
                    val aboveSkylightLevel = ctx.skylightAt(x, currentY + 1, z)
                    val aboveNoise = generateTerrainNoise(x, currentY + 1, z, terrainNoiseStrength * 0.7f)
                    val aboveLightInfluence = aboveSkylightLevel * 0.01f
                    val transformedAbove =
                        materialTransformer.transformMaterial(
                            aboveState,
                            1.0f - power + (aboveNoise * 0.2f) + aboveLightInfluence,
                            x, currentY + 1, z
                        )
                    ctx.setState(x, currentY + 1, z, transformedAbove)
                }
            } else {
                consecutiveTerrainBlocks = 0

                if (shouldConvertToAir) {
                    ctx.setState(
                        x,
                        currentY,
                        z,
                        Blocks.AIR.defaultBlockState(),
                        updateBlock = false
                    )
                } else {
                    val skylightLevel = ctx.skylightAt(x, currentY, z)
                    val blockNoise = generateTerrainNoise(x, currentY, z, terrainNoiseStrength * 0.5f)
                    val lightInfluence = skylightLevel * 0.00667f
                    val transformedBlock =
                        materialTransformer.transformMaterial(
                            currentState,
                            1.0f - power + (blockNoise * 0.2f) + lightInfluence,
                            x, currentY, z
                        )
                    ctx.setState(x, currentY, z, transformedBlock, updateBlock = false)
                }
            }
        }
    }

    private fun isWallLike(x: Int, y: Int, z: Int, ctx: ColumnCarveContext): Boolean {
        val east = ctx.originalStateAt(x + 1, y, z).isAir
        val west = ctx.originalStateAt(x - 1, y, z).isAir
        if (east && west) return true

        val south = ctx.originalStateAt(x, y, z + 1).isAir
        if ((east || west) && south) return true

        val north = ctx.originalStateAt(x, y, z - 1).isAir
        return ((east || west) && north) || (south && north)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun generateTerrainNoise(x: Int, y: Int, z: Int, strength: Float): Float {
        val seed = ((x * 374761393L + y * 668265263L + z * 1274126177L) and 0x7FFFFFFF).toInt()
        val random = Random(seed)
        val noise1 = (random.nextDouble() - 0.5).toFloat()
        val noise2 = ((random.nextDouble() - 0.5) * 0.5).toFloat()
        val noise3 = ((random.nextDouble() - 0.5) * 0.25).toFloat()
        return ((noise1 + noise2 + noise3) * 1.143f * strength).coerceIn(-1.0f, 1.0f)
    }
}