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

package com.mochibit.defcon.biomes.definitions

import com.mochibit.defcon.biomes.*
import com.mochibit.defcon.biomes.data.BiomeEffects
import com.mochibit.defcon.biomes.data.BiomeInfo
import com.mochibit.defcon.biomes.data.BiomeParticle
import com.mochibit.defcon.biomes.enums.PrecipitationType
import com.mochibit.defcon.biomes.enums.TemperatureModifier
import org.bukkit.Particle

@BiomeInfo("nuclear_fallout")
class NuclearFalloutBiome : CustomBiome() {
    override fun build(): CustomBiome {
        val builder = CustomBiomeBuilder()
            .setPrecipitation(PrecipitationType.RAIN)
            .setTemperature(0.8f)
            .setDownfall(0.4f)
            .setTemperatureModifier(TemperatureModifier.NONE)
            .setHasPrecipitation(true)
            .setEffects(
                BiomeEffects(
                    skyColor = 7175279,
                    fogColor = 9016715,
                    waterColor = 6187104,
                    waterFogColor = 4804682,
                    particle = BiomeParticle(
                        particle = Particle.WHITE_ASH,
                        probability = 0.05f
                    )
                )
            )

        return builder.build()
    }
}