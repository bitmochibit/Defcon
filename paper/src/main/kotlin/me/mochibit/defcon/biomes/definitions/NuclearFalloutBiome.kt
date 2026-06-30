/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package me.mochibit.defcon.biomes.definitions

import me.mochibit.defcon.biomes.*
import me.mochibit.defcon.biomes.data.*
import me.mochibit.defcon.biomes.enums.PrecipitationType
import me.mochibit.defcon.biomes.enums.TemperatureModifier
import org.bukkit.Particle

@BiomeInfo("nuclear_fallout")
object NuclearFalloutBiome : CustomBiome() {
    init {
        precipitation = PrecipitationType.RAIN
        temperature = 0.8f
        downfall = 0.4f
        temperatureModifier = TemperatureModifier.NONE
        hasPrecipitation = true
        effects = BiomeEffects(
            skyColor = 7175279,
            fogColor = 9016715,
            waterColor = 6187104,
            waterFogColor = 4804682,
            particle = BiomeParticle(
                particle = Particle.WHITE_ASH,
                probability = 0.05f
            ),
            ambientSound = "minecraft:ambient.soul_sand_valley.loop",
            moodSound = BiomeMoodSound(
                sound = "minecraft:ambient.soul_sand_valley.mood",
                tickDelay = 1000,
                blockSearchExtent = 0,
                offset = 5.0f
            ),
            additionsSound = BiomeAdditionsSound(
                sound = "minecraft:ambient.soul_sand_valley.additions",
                tickChance = 0.6
            )
        )
    }
}