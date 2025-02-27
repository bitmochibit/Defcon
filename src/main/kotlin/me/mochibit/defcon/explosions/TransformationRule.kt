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
import java.util.EnumSet
import kotlin.random.Random

class TransformationRule {
    companion object {
        // Using EnumSet for better performance with enum types
        val BLOCK_TRANSFORMATION_BLACKLIST: Set<Material> = EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL
        )

        val LIQUID_MATERIALS: Set<Material> = EnumSet.of(
            Material.WATER,
            Material.LAVA
        )

        val DEAD_PLANTS: List<Material> = listOf(
            Material.DEAD_BUSH,
            Material.WITHER_ROSE
        )

        val BURNT_BLOCK: List<Material> = listOf(
            Material.COBBLED_DEEPSLATE,
            Material.BLACK_CONCRETE_POWDER,
            Material.OBSIDIAN
        )

        val LIGHT_WEIGHT_BLOCKS: Set<Material> = EnumSet.noneOf(Material::class.java).apply {
            add(Material.ICE)
            add(Material.PACKED_ICE)
            add(Material.BLUE_ICE)
            add(Material.FROSTED_ICE)
            add(Material.SNOW)
            add(Material.SNOW_BLOCK)
            add(Material.POWDER_SNOW)
        }

        val PLANTS: Set<Material> = EnumSet.noneOf(Material::class.java).apply {
            // Group related plants for better readability
            // Grass types
            add(Material.GRASS)
            add(Material.TALL_GRASS)
            add(Material.FERN)
            add(Material.LARGE_FERN)

            // Saplings
            add(Material.OAK_SAPLING)
            add(Material.BIRCH_SAPLING)
            add(Material.JUNGLE_SAPLING)
            add(Material.ACACIA_SAPLING)
            add(Material.DARK_OAK_SAPLING)
            add(Material.SPRUCE_SAPLING)
            add(Material.CHERRY_SAPLING)
            add(Material.BAMBOO_SAPLING)

            // Flowers
            add(Material.POPPY)
            add(Material.DANDELION)
            add(Material.BLUE_ORCHID)
            add(Material.ALLIUM)
            add(Material.AZURE_BLUET)
            add(Material.OXEYE_DAISY)
            add(Material.CORNFLOWER)
            add(Material.LILY_OF_THE_VALLEY)
            add(Material.PINK_PETALS)
            add(Material.LILAC)
            add(Material.PEONY)
            add(Material.SUNFLOWER)
            add(Material.RED_TULIP)
            add(Material.ORANGE_TULIP)
            add(Material.WHITE_TULIP)
            add(Material.PINK_TULIP)
        }

        // Pre-compiled regex patterns for better performance
        private val SAPLING_PATTERN = Regex(".*sapling.*", RegexOption.IGNORE_CASE)
        private val SLAB_WALL_STAIRS_PATTERN = Regex(".*_(SLAB|WALL|STAIRS)")
    }

    // Use a more efficient lookup approach with a function reference map
    private val directTransformations = mapOf(
        Material.GRASS_BLOCK to ::transformGrassBlock,
        Material.DIRT to ::transformDirt,
        Material.STONE to { Material.COBBLED_DEEPSLATE },
        Material.COBBLESTONE to { Material.COBBLED_DEEPSLATE }
    )

    // Cache random generator for better performance
    private val random = Random.Default

    // Main transformation function
    fun transformMaterial(currentMaterial: Material, normalizedExplosionPower: Double): Material {
        // Early return for blacklisted materials
        if (currentMaterial in BLOCK_TRANSFORMATION_BLACKLIST) {
            return currentMaterial
        }

        // High explosion power creates burnt blocks
        if (normalizedExplosionPower > 0.8 && currentMaterial !in LIGHT_WEIGHT_BLOCKS) {
            return getRandomBurntBlock()
        }

        // Handle specific material types
        return when {
            currentMaterial in LIGHT_WEIGHT_BLOCKS -> Material.AIR
            currentMaterial in directTransformations -> directTransformations[currentMaterial]?.invoke() ?: Material.AIR
            currentMaterial in PLANTS -> transformToDeadPlantOrAir(normalizedExplosionPower)
            SAPLING_PATTERN.matches(currentMaterial.name) -> transformToDeadPlantOrAir(normalizedExplosionPower)
            SLAB_WALL_STAIRS_PATTERN.matches(currentMaterial.name) -> transformSlabWallStairs(currentMaterial)
            else -> Material.AIR // Default fallback
        }
    }

    // Optimized random selection functions
    private fun getRandomDeadPlant(): Material = DEAD_PLANTS[random.nextInt(DEAD_PLANTS.size)]
    private fun getRandomBurntBlock(): Material = BURNT_BLOCK[random.nextInt(BURNT_BLOCK.size)]

    // Specific transformation logic
    private fun transformToDeadPlantOrAir(normalizedExplosionPower: Double): Material {
        return if (normalizedExplosionPower > 0.5) Material.AIR else getRandomDeadPlant()
    }

    private fun transformSlabWallStairs(material: Material): Material {
        return when {
            material.name.endsWith("_SLAB") -> Material.COBBLED_DEEPSLATE_SLAB
            material.name.endsWith("_WALL") -> Material.COBBLED_DEEPSLATE_WALL
            material.name.endsWith("_STAIRS") -> Material.COBBLED_DEEPSLATE_STAIRS
            else -> Material.AIR // Fallback
        }
    }

    private fun transformGrassBlock(): Material = if (random.nextBoolean()) Material.COARSE_DIRT else Material.DIRT
    private fun transformDirt(): Material = if (random.nextBoolean()) Material.COARSE_DIRT else Material.COBBLED_DEEPSLATE
}