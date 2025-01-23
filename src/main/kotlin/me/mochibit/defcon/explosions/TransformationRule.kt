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

package me.mochibit.defcon.explosions

import org.bukkit.Material
import kotlin.random.Random

class TransformationRule {
    companion object {
        val BLOCK_TRANSFORMATION_BLACKLIST = hashSetOf(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL
        )
        val LIQUID_MATERIALS = hashSetOf(Material.WATER, Material.LAVA)
        val DEAD_PLANTS = hashSetOf(
            Material.DEAD_BUSH,
            Material.WITHER_ROSE
        )
        val BURNT_BLOCK = hashSetOf(
            Material.COBBLED_DEEPSLATE,
            Material.BLACK_CONCRETE_POWDER,
            Material.OBSIDIAN,
        )
        val LIGHT_WEIGHT_BLOCKS = hashSetOf(
            Material.ICE,
            Material.PACKED_ICE,
            Material.BLUE_ICE,
            Material.FROSTED_ICE,
            Material.SNOW,
            Material.SNOW_BLOCK,
            Material.POWDER_SNOW,
        )
        val PLANTS = hashSetOf(
            Material.GRASS,
            Material.TALL_GRASS,

            Material.FERN,
            Material.LARGE_FERN,

            Material.OAK_SAPLING,
            Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.SPRUCE_SAPLING,
            Material.CHERRY_SAPLING,
            Material.BAMBOO_SAPLING,

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
        )
    }

    private val transformationMap = mapOf(
        Material.GRASS_BLOCK to ::transformGrassBlock,
        Material.DIRT to ::transformDirt,
        Material.STONE to { Material.COBBLED_DEEPSLATE },
        Material.COBBLESTONE to { Material.COBBLED_DEEPSLATE },
    )

    private val regexTransformationMap: Map<Regex, (Material, Double) -> Material> = mapOf(
        Regex(".*sapling.*", RegexOption.IGNORE_CASE) to { _, normalizedExplosionPower ->
            transformToDeadPlantOrAir(normalizedExplosionPower)
        },

        Regex(".*_(SLAB|WALL|STAIRS)") to { material, _ ->
            when {
                material.name.endsWith("_SLAB") -> Material.COBBLED_DEEPSLATE_SLAB
                material.name.endsWith("_WALL") -> Material.COBBLED_DEEPSLATE_WALL
                material.name.endsWith("_STAIRS") -> Material.COBBLED_DEEPSLATE_STAIRS
                else -> Material.AIR // Fallback
            }
        }
    )

    private fun transformToDeadPlantOrAir(normalizedExplosionPower: Double): Material {
        return if (normalizedExplosionPower > 0.5) Material.AIR else DEAD_PLANTS.random()
    }


    // Custom rules for materials based on name suffix
    fun customTransformation(currentMaterial: Material, normalizedExplosionPower: Double): Material {
        if (normalizedExplosionPower > 0.8 && currentMaterial !in LIGHT_WEIGHT_BLOCKS)
            return BURNT_BLOCK.random()

        return when (currentMaterial) {
            in LIGHT_WEIGHT_BLOCKS -> Material.AIR
            in transformationMap -> transformationMap[currentMaterial]?.invoke() ?: Material.AIR
            in PLANTS -> transformToDeadPlantOrAir(normalizedExplosionPower)
            // Check if the block type matches any of the regex patterns
            else -> regexTransformationMap.entries.firstOrNull { (regex, _) -> regex.matches(currentMaterial.name) }
                ?.value?.invoke(currentMaterial, normalizedExplosionPower) ?: Material.AIR
        }
    }

    private fun transformGrassBlock(): Material = if (Random.nextBoolean()) Material.COARSE_DIRT else Material.DIRT
    private fun transformDirt(): Material =
        if (Random.nextBoolean()) Material.COARSE_DIRT else Material.COBBLED_DEEPSLATE
}
