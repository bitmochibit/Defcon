package me.mochibit.defcon.content.explosion.processor.worldgen

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.WorldgenTreeBurner
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.explosion.processor.Shockwave
import net.minecraft.core.BlockPos
import net.minecraft.tags.BiomeTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object PostApocalypticTerrain {

    const val FEATHER_RADIUS_BLOCKS = 24

    private const val EDGE_FEATHER = 20.0
    private const val WOBBLE_FACTOR = 0.12
    private const val MAX_CRATER_DEPTH = 10
    private const val WITHER_ROSE_CHANCE = 0.15f

    fun maxFeatherMargin(radius: Int): Double = EDGE_FEATHER + radius * WOBBLE_FACTOR


    fun apply(level: WorldGenLevel, chunk: ChunkAccess, zone: BlastZone) {
        val chunkPos = chunk.pos
        val random = RandomSource.create(chunkPos.toLong())
        val transformer = MaterialTransformer(random = Random(chunkPos.toLong() xor 0x5DEECE66DL))
        val seaLevel = level.seaLevel


        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val worldX = chunkPos.minBlockX + x
                val worldZ = chunkPos.minBlockZ + z

                val dx = (worldX - zone.centerX).toDouble()
                val dz = (worldZ - zone.centerZ).toDouble()
                val dist = sqrt(dx * dx + dz * dz).toFloat()

                val angle = atan2(dz, dx)
                val effectiveRadius = zone.radius + organicWobble(zone, angle).toFloat()

                val explosionPower = Shockwave.calculateShockwavePower(dist/effectiveRadius)

                val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)


                val craterDepth = (MAX_CRATER_DEPTH * explosionPower).toInt().coerceAtLeast(1)
                val minTouchY = (surfaceY - craterDepth).coerceAtLeast(chunk.minBuildHeight).coerceAtLeast(seaLevel)
                val maxTouchY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z).coerceAtLeast(surfaceY) + 32

                var y = minTouchY
                while (y <= maxTouchY) {
                    val section = chunk.sections.getOrNull(chunk.getSectionIndex(y))
                    if (section != null && section.hasOnlyAir()) {
                        y = ((y - chunk.minBuildHeight) / 16 + 1) * 16 + chunk.minBuildHeight
                        continue
                    }

                    val pos = BlockPos(worldX, y, worldZ)

                    val state = chunk.getBlockState(pos)
                    val block = state.block

                    when {
                        state.isAir -> {}

                        block in TreeBurnCore.LOG_BLOCKS || block in TreeBurnCore.WOOD_BLOCKS -> {
                            WorldgenTreeBurner.burnTree(
                                level = level,
                                trunkOrAnyLogPos = pos,
                                zoneCenterX = zone.centerX,
                                zoneCenterZ = zone.centerZ,
                                explosionPower = explosionPower,

                            )
                        }
                        block in TreeBurnCore.LEAF_BLOCKS -> {
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false)
                        }


                        state.`is`(Blocks.WATER) -> {
                        }

                        else -> {
                            val transformed = transformer.transformMaterial(state, explosionPower, worldX, worldZ, y)
                            if (transformed !== state  ) {
                                if (explosionPower <= 0.3 && random.nextFloat() < explosionPower) {
                                    chunk.setBlockState(pos, transformed, false)
                                } else {
                                    chunk.setBlockState(pos, transformed, false)
                                }
                            }
                        }
                    }
                    y++
                }
            }
        }
    }

    private fun organicWobble(zone: BlastZone, angle: Double): Double {
        val seed = zone.centerX * 341873128712L + zone.centerZ * 132897987541L
        val phase1 = (seed % 1000) / 1000.0 * Math.PI * 2
        val phase2 = ((seed / 7) % 1000) / 1000.0 * Math.PI * 2
        val amp = zone.radius * 0.12
        return amp * (sin(angle * 3 + phase1) * 0.6 + sin(angle * 7 + phase2) * 0.4)
    }
}