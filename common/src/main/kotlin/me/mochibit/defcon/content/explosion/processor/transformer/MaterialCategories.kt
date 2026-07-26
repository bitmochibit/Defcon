package me.mochibit.defcon.content.explosion.processor.transformer

import me.mochibit.defcon.foundation.services.contentService
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.GrassBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.state.BlockState
import kotlin.lazy

object MaterialCategories {
    val INDESTRUCTIBLE_BLOCKS: Set<BlockState> =
        setOf(
            Blocks.BEDROCK.defaultBlockState(),
            Blocks.BARRIER.defaultBlockState(),
            Blocks.COMMAND_BLOCK.defaultBlockState(),
            Blocks.END_PORTAL_FRAME.defaultBlockState(),
            Blocks.END_PORTAL.defaultBlockState(),
        )

    val LIQUID_MATERIALS: Set<BlockState> =
        setOf(
            Blocks.WATER.defaultBlockState(),
            Blocks.LAVA.defaultBlockState(),
        )

    val DEAD_PLANTS: Set<BlockState> =
        setOf(
            Blocks.DEAD_BUSH.defaultBlockState(),
            Blocks.WITHER_ROSE.defaultBlockState(),
        )

    val DESTROYED_BLOCK: Set<BlockState> =
        setOf(
            Blocks.TUFF.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
        )

    val LIGHT_BLOCKS: Set<BlockState> =
        setOf(
            Blocks.GLOWSTONE.defaultBlockState(),
            Blocks.SHROOMLIGHT.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState(),
            Blocks.LANTERN.defaultBlockState(),
            Blocks.TORCH.defaultBlockState(),
            Blocks.SOUL_TORCH.defaultBlockState(),
            Blocks.CAMPFIRE.defaultBlockState(),
            Blocks.SOUL_CAMPFIRE.defaultBlockState(),
        )

    // Terrain blocks
    val DIRTY_TERRAIN_BLOCKS: Set<BlockState> by lazy {
            buildSet {
                addAll(
                    BuiltInRegistries.BLOCK
                    .filter{
                        when (it) {
                            is GrassBlock -> true
                            else -> false
                        }
                    }
                    .map { it.defaultBlockState() }
                )
                add(Blocks.DIRT.defaultBlockState())
                add(Blocks.COARSE_DIRT.defaultBlockState())
                add(Blocks.MYCELIUM.defaultBlockState())
                add(Blocks.CLAY.defaultBlockState())
                add(Blocks.MUD.defaultBlockState())
                add(Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState())
            }
        }


    val SANDY_TERRAIN_BLOCKS: Set<BlockState> by lazy {
        setOf(
            Blocks.SAND.defaultBlockState(),
            Blocks.RED_SAND.defaultBlockState(),
            Blocks.SOUL_SAND.defaultBlockState(),
            Blocks.SOUL_SOIL.defaultBlockState(),
        )
    }

    val STONY_TERRAIN_BLOCKS: Set<BlockState> by lazy {
        setOf(
            Blocks.STONE.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(),
        )
    }

    val TERRAIN_BLOCKS: Set<BlockState>
        get() = DIRTY_TERRAIN_BLOCKS + SANDY_TERRAIN_BLOCKS + STONY_TERRAIN_BLOCKS



    val LIGHT_WEIGHT_BLOCKS: Set<BlockState> =
        setOf(
            Blocks.ICE.defaultBlockState(),
            Blocks.PACKED_ICE.defaultBlockState(),
            Blocks.BLUE_ICE.defaultBlockState(),
            Blocks.FROSTED_ICE.defaultBlockState(),
            Blocks.SNOW.defaultBlockState(),
            Blocks.SNOW_BLOCK.defaultBlockState(),
            Blocks.POWDER_SNOW.defaultBlockState(),
        )

    val VINES: Set<BlockState> by lazy {
        buildSet {
            addAll(BuiltInRegistries.BLOCK
                .filter{
                    when (it) {
                        is VineBlock -> true
                        else -> false
                    }
                }
                .map { it.defaultBlockState() }
            )
        }
    }


    val PLANTS: Set<BlockState> by lazy {
        buildSet {
            addAll(BuiltInRegistries.BLOCK
                .filter{
                    when (it) {
                        is BushBlock -> true
                        else -> false
                    }
                }
                .map { it.defaultBlockState() }
            )
        }
    }

    // Helper functions for categories

    fun isSlab(state: BlockState): Boolean =
        state.`is`(BlockTags.SLABS)

    fun isWall(state: BlockState): Boolean =
        state.`is`(BlockTags.WALLS)

    fun isStairs(state: BlockState): Boolean =
        state.`is`(BlockTags.STAIRS)

    fun isGlass(state: BlockState): Boolean =
        contentService.isGlass(state)
}