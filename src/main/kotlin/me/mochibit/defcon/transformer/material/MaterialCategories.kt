/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.transformer.material

import org.bukkit.Material

object MaterialCategories {
    val INDESTRUCTIBLE_BLOCKS: Set<Material> =
        setOf(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL,
        )

    val LIQUID_MATERIALS: Set<Material> =
        setOf(
            Material.WATER,
            Material.LAVA,
        )

    val DEAD_PLANTS: Set<Material> =
        setOf(
            Material.DEAD_BUSH,
            Material.WITHER_ROSE,
        )

    val DESTROYED_BLOCK: Set<Material> =
        setOf(
            Material.TUFF,
            Material.DEEPSLATE,
            Material.COBBLESTONE,
            Material.ANDESITE,
        )

    val LIGHT_BLOCKS: Set<Material> =
        setOf(
            Material.GLOWSTONE,
            Material.SHROOMLIGHT,
            Material.SEA_LANTERN,
            Material.LANTERN,
            Material.TORCH,
            Material.SOUL_TORCH,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
        )

    val TERRAIN_BLOCKS: Set<Material> =
        setOf(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.MYCELIUM,
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL,
            Material.CLAY,
            Material.SOUL_SAND,
            Material.SOUL_SOIL,
            Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS,
            Material.STONE,
            Material.TERRACOTTA,
        )

    val LIGHT_WEIGHT_BLOCKS: Set<Material> =
        setOf(
            Material.ICE,
            Material.PACKED_ICE,
            Material.BLUE_ICE,
            Material.FROSTED_ICE,
            Material.SNOW,
            Material.SNOW_BLOCK,
            Material.POWDER_SNOW,
        )

    val PLANTS: Set<Material> by lazy {
        buildSet {
            // Grass types
            addAll(
                listOf(
                    Material.SHORT_GRASS,
                    Material.TALL_GRASS,
                    Material.FERN,
                    Material.LARGE_FERN,
                ),
            )

            // Saplings - filter all materials that contain "SAPLING"
            addAll(Material.entries.filter { "SAPLING" in it.name })

            // Flowers
            addAll(
                listOf(
                    Material.POPPY,
                    Material.DANDELION,
                    Material.BLUE_ORCHID,
                    Material.ALLIUM,
                    Material.AZURE_BLUET,
                    Material.OXEYE_DAISY,
                    Material.CORNFLOWER,
                    Material.LILY_OF_THE_VALLEY,
                    Material.PINK_PETALS,
                    Material.LILAC,
                    Material.PEONY,
                    Material.SUNFLOWER,
                    Material.RED_TULIP,
                    Material.ORANGE_TULIP,
                    Material.WHITE_TULIP,
                    Material.PINK_TULIP,
                ),
            )
        }
    }

    // Helper functions for categories
    fun isSlab(material: Material): Boolean = material.name.endsWith("_SLAB")

    fun isWall(material: Material): Boolean = material.name.endsWith("_WALL")

    fun isStairs(material: Material): Boolean = material.name.endsWith("_STAIRS")

    fun isGlass(material: Material): Boolean = material.name.contains("GLASS", ignoreCase = true)
}
