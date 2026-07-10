package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

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

    val TERRAIN_BLOCKS: Set<BlockState> =
        setOf(
            Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.MYCELIUM.defaultBlockState(),
            Blocks.SAND.defaultBlockState(),
            Blocks.RED_SAND.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.CLAY.defaultBlockState(),
            Blocks.SOUL_SAND.defaultBlockState(),
            Blocks.SOUL_SOIL.defaultBlockState(),
            Blocks.MUD.defaultBlockState(),
            Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(),
        )

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

    val PLANTS: Set<BlockState> by lazy {
        buildSet {
            // Grass types
            addAll(
                listOf(
                    Blocks.SHORT_GRASS.defaultBlockState(),
                    Blocks.TALL_GRASS.defaultBlockState(),
                    Blocks.FERN.defaultBlockState(),
                    Blocks.LARGE_FERN.defaultBlockState(),
                ),
            )

            // Saplings - filter all materials that contain "SAPLING"
            addAll(BuiltInRegistries.BLOCK.filter { it.name.getString(100).contains("sapling") }.map { it.defaultBlockState() })

            // Flowers
            addAll(
                listOf(
                    Blocks.POPPY.defaultBlockState(),
                    Blocks.DANDELION.defaultBlockState(),
                    Blocks.BLUE_ORCHID.defaultBlockState(),
                    Blocks.ALLIUM.defaultBlockState(),
                    Blocks.AZURE_BLUET.defaultBlockState(),
                    Blocks.OXEYE_DAISY.defaultBlockState(),
                    Blocks.CORNFLOWER.defaultBlockState(),
                    Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
                    Blocks.PINK_PETALS.defaultBlockState(),
                    Blocks.LILAC.defaultBlockState(),
                    Blocks.PEONY.defaultBlockState(),
                    Blocks.SUNFLOWER.defaultBlockState(),
                    Blocks.RED_TULIP.defaultBlockState(),
                    Blocks.ORANGE_TULIP.defaultBlockState(),
                    Blocks.WHITE_TULIP.defaultBlockState(),
                    Blocks.PINK_TULIP.defaultBlockState(),
                ),
            )
        }
    }

    // Helper functions for categories
    fun isSlab(state: BlockState): Boolean = state.block.name.getString(100).endsWith("_slab")

    fun isWall(state: BlockState): Boolean = state.block.name.getString(100).endsWith("_wall")

    fun isStairs(state: BlockState): Boolean = state.block.name.getString(100).endsWith("_stairs")

    fun isGlass(state: BlockState): Boolean = state.block.name.getString(100).contains("glass", ignoreCase = true)
}