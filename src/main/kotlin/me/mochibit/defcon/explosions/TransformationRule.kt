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
            Material.END_PORTAL,
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

        val DESTROYED_BLOCK: List<Material> = listOf(
            Material.COBBLESTONE,
            Material.COBBLED_DEEPSLATE
        )

        val DIRT_TRANSFORMATIONS: List<Material> = listOf(
            Material.COARSE_DIRT,
            Material.DIRT
        )

        val SLAB_TRANSFORMATIONS: EnumSet<Material> = EnumSet.of(
            Material.COBBLED_DEEPSLATE_SLAB,
            Material.COBBLESTONE_SLAB
        )

        val WALL_TRANSFORMATIONS: EnumSet<Material> = EnumSet.of(
            Material.COBBLED_DEEPSLATE_WALL,
            Material.COBBLESTONE_WALL
        )

        val STAIRS_TRANSFORMATIONS: EnumSet<Material> = EnumSet.of(
            Material.COBBLED_DEEPSLATE_STAIRS,
            Material.COBBLESTONE_STAIRS
        )


        // Group categorization for cleaner code and better performance
        val LIGHT_WEIGHT_BLOCKS: Set<Material> = EnumSet.noneOf(Material::class.java).apply {
            addAll(listOf(
                Material.ICE,
                Material.PACKED_ICE,
                Material.BLUE_ICE,
                Material.FROSTED_ICE,
                Material.SNOW,
                Material.SNOW_BLOCK,
                Material.POWDER_SNOW
            ))
        }

        val PLANTS: Set<Material> = EnumSet.noneOf(Material::class.java).apply {
            // Group related plants for better readability
            // Grass types
            addAll(listOf(
                Material.GRASS,
                Material.TALL_GRASS,
                Material.FERN,
                Material.LARGE_FERN
            ))

            // Saplings
            for (material in Material.entries){
               if (material.name.contains("SAPLING", ignoreCase = true)){
                   add(material)
               }
           }

            // Flowers
            addAll(listOf(
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
                Material.PINK_TULIP
            ))
        }

        val SLABS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply{
           for (material in Material.entries){
               if (material.name.endsWith("_SLAB")){
                   add(material)
               }
           }
        }

        val WALLS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply{
           for (material in Material.entries){
               if (material.name.endsWith("_WALL")){
                   add(material)
               }
           }
        }

        val STAIRS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply{
           for (material in Material.entries){
               if (material.name.endsWith("_STAIRS")){
                   add(material)
               }
           }
        }

        val GLASS: EnumSet<Material> = EnumSet.noneOf(Material::class.java).apply{
           for (material in Material.entries){
               if (material.name.contains("GLASS", ignoreCase = true)){
                   add(material)
               }
           }
        }
    }

    // Cache random generator for better performance
    private val random = Random.Default

    // Main transformation function
    fun transformMaterial(currentMaterial: Material, normalizedExplosionPower: Double): Material {
        // Early return for blacklisted materials
        if (currentMaterial in BLOCK_TRANSFORMATION_BLACKLIST) {
            return currentMaterial
        }

        // High explosion power creates burnt blocks for most materials
        if (normalizedExplosionPower > 0.8 && currentMaterial !in LIGHT_WEIGHT_BLOCKS) {
            return getRandomBurntBlock()
        }

        if (currentMaterial in SLABS || currentMaterial in WALLS || currentMaterial in STAIRS || currentMaterial in GLASS){
            return when(currentMaterial){
                in SLABS -> SLAB_TRANSFORMATIONS.random(random)
                in WALLS -> WALL_TRANSFORMATIONS.random(random)
                in STAIRS -> STAIRS_TRANSFORMATIONS.random(random)
                in GLASS -> Material.AIR
                else -> currentMaterial
            }
        }

        // Handle specific material types
        return when (currentMaterial) {
            in LIGHT_WEIGHT_BLOCKS -> Material.AIR
            in PLANTS -> transformToDeadPlantOrAir(normalizedExplosionPower)
            Material.DIRT, Material.GRASS_BLOCK-> DIRT_TRANSFORMATIONS.random(random)
            else ->  DESTROYED_BLOCK.random(random)
        }
    }

    // Optimized random selection functions with direct indexing
    private fun getRandomDeadPlant(): Material = DEAD_PLANTS[random.nextInt(DEAD_PLANTS.size)]
    private fun getRandomBurntBlock(): Material = BURNT_BLOCK[random.nextInt(BURNT_BLOCK.size)]

    // Specific transformation logic
    private fun transformToDeadPlantOrAir(normalizedExplosionPower: Double): Material {
        return if (normalizedExplosionPower > 0.5) Material.AIR else getRandomDeadPlant()
    }

}