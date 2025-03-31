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

@BiomeInfo("nuclear_fallout")
class NuclearFalloutBiome : CustomBiome() {
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
            )
        )
    }
}