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
import me.mochibit.defcon.biomes.data.BiomeEffects
import me.mochibit.defcon.biomes.data.BiomeInfo
import me.mochibit.defcon.biomes.data.BiomeParticle
import me.mochibit.defcon.biomes.enums.PrecipitationType
import me.mochibit.defcon.biomes.enums.TemperatureModifier
import org.bukkit.Particle

@BiomeInfo("burning_air")
object BurningAirBiome : CustomBiome() {
    init {
        precipitation = PrecipitationType.NONE
        temperature = 2.0f
        downfall = 0.1f
        temperatureModifier = TemperatureModifier.NONE
        hasPrecipitation = false
        effects = BiomeEffects(
            skyColor = 16557138,
            fogColor = 16739888,
            waterColor = 4159204,
            waterFogColor = 329011,
            particle = BiomeParticle(
                particle = Particle.LAVA,
                probability = 0.005f
            )
        )
    }
}