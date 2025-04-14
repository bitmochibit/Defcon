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

package me.mochibit.defcon.biomes

import me.mochibit.defcon.biomes.data.BiomeInfo
import org.reflections.Reflections

class BiomeRegister {
    companion object {
        private val biomeList: MutableList<CustomBiome> = mutableListOf()

        init {
            registerDefaultBiomes()
        }

        private fun registerDefaultBiomes() {
            val packageName = CustomBiome::class.java.`package`?.name
            val reflections = Reflections(packageName)

            val defaultBiomeClasses = reflections.getTypesAnnotatedWith(BiomeInfo::class.java)

            defaultBiomeClasses
                .filter { CustomBiome::class.java.isAssignableFrom(it) }
                .forEach { clazz ->
                    try {
                        val biome = clazz.getField("INSTANCE").get(null) as CustomBiome
                        biomeList.add(biome)
                    } catch (e: Exception) {
                        println("Failed to register biome: ${clazz.name}")
                        e.printStackTrace()
                    }
                }
        }
    }

    fun registerBiome(biome: CustomBiome) {
        biomeList.add(biome)
    }

    fun getBiomes(): List<CustomBiome> {
        return biomeList
    }
}