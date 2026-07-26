package me.mochibit.defcon.content.explosion.processor.carver

import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.foundation.util.roll
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
        val terrainNoiseStrength = power * 0.7f
        val baseTerrainBreakChance = power * 0.95f

        val spatialNoise = (generateTerrainNoise(x, 0, z, 1.0f) + 1.0f) * 0.5f
        if (power < 0.3f && spatialNoise > (power / 0.3f)) {
            return
        }

        var consecutiveTerrainBlocks = 0
        var trunkTopY: Int? = null
        var trunkBaseY: Int? = null
        var treeVerified: Boolean? = null
        var consecutiveAirBlocks = 0
        var consecutiveFluids = 0
        var consecutiveBlacklisted = 0
        var wasProcessingTree = false

        for (currentY in topY downTo maxOf(seaLevelMinus3, worldMinHeight)) {
            if (ctx.isPosAlreadyBurnedByTree(x, currentY, z)) continue


            val currentState = if (currentY == topY) firstBlockState else ctx.getState(x, currentY, z)


            if (ctx.isTreeBlock(x, currentY, z)) {
                if (treeVerified == null) {
                    treeVerified = ctx.isColumnTreeLike(x, currentY, z)
                }

                if (treeVerified == true) {
                    if (trunkTopY == null && ctx.isLogOrWood(currentState)) {
                        trunkTopY = currentY
                        trunkBaseY = ctx.findLocalTrunkBase(x, currentY, z)
                    }
                    ctx.burnTreeBlock(x, currentY, z, power.toDouble(), trunkTopY, trunkBaseY)
                    wasProcessingTree = true
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    consecutiveFluids = 0
                    consecutiveBlacklisted = 0
                    continue
                }
            }

            if (wasProcessingTree) {
                wasProcessingTree = false
                if (!currentState.isAir && currentState.fluidState.isEmpty
                    && currentState in MaterialCategories.TERRAIN_BLOCKS
                ) {
                    ctx.setState(x, currentY, z, Blocks.ROOTED_DIRT.defaultBlockState())
                    continue
                }
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

            val isLeafOrVine = ctx.isLeafBlock(x, currentY, z) || currentState in MaterialCategories.VINES

            if (!isLeafOrVine && isWallLike(x, currentY, z, ctx)) {
                consecutiveTerrainBlocks = 0

                if (shouldConvertToAir) {
                    ctx.setState(x, currentY, z, Blocks.AIR.defaultBlockState(), updateBlock = false)
                    continue
                }

                val skylightLevel = ctx.skylightAt(x, currentY, z)
                val lightExposure = (skylightLevel.toFloat() / 15f).coerceIn(0f, 1f)
                val breakChance = power * (0.1f + lightExposure * 0.8f)

                if (breakChance.roll()) {
                    ctx.setState(x, currentY, z, Blocks.AIR.defaultBlockState(), updateBlock = false)
                } else if (shouldTransformMaterial(power)) {
                    val transformedBlock = materialTransformer.transformMaterial(currentState, power, x, currentY, z)
                    ctx.setState(x, currentY, z, transformedBlock)
                }
                continue
            }

            if (isTerrainBlock) {
                if (++consecutiveTerrainBlocks >= 3) break
                val noiseValue = generateTerrainNoise(x, currentY, z, terrainNoiseStrength)

                val skylightLevel = ctx.skylightAt(x, currentY, z)
                val noiseInfluence = noiseValue * 0.3f
                val lightInfluence = skylightLevel * 0.0133f
                val transformedBlock =
                    materialTransformer.transformMaterial(
                        currentState,
                        power + noiseInfluence + lightInfluence, x, currentY, z
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
                            power + (aboveNoise * 0.2f) + aboveLightInfluence,
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
                            power + (blockNoise * 0.2f) + lightInfluence,
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

    private fun shouldTransformMaterial(power: Float): Boolean = Random.nextFloat() < power

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