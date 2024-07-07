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

package com.mochibit.defcon.effects

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import org.bukkit.Color
import org.bukkit.Location

class TemperatureComponent(
    particleShape: ParticleShape,
    var minTemperatureEmission: Double = 1500.0,
    var maxTemperatureEmission: Double = 4000.0,
    var minTemperature: Double = 40.0,
    var maxTemperature: Double = 6000.0,

    var smokeColor: Color = Color.fromRGB(44, 41, 42), // #2C292A
    var baseColor: Color = Color.fromRGB(255, 121, 0) // #F7E381
) : BaseComponent(particleShape) {
    var temperatureCoolingRate = .0
    var temperature: Double = maxTemperature
        set(value) {
            field = value.coerceIn(minTemperature, maxTemperature)
        }


    /**
     * The particle slowly cool downs to the minimum temperature, then the color will transition to black smoke
     */
    fun applyHeatedSmokeColor(): TemperatureComponent {
        // Sets the color supplier to apply the temperature emission
        colorSupplier = {
            blackBodyEmission()
        }
        return this
    }

    private fun blackBodyEmission(): Color {
        temperature -= temperatureCoolingRate
        val ratio = MathFunctions.remap(temperature, minTemperature, maxTemperature, 0.0, 1.0)
        val scaledRatio = ratio * (temperatureEmissionGradient.size - 1)
        val index = scaledRatio.toInt()
        val remainder = scaledRatio - index

        if (index >= temperatureEmissionGradient.size - 1)
            return temperatureEmissionGradient.last()
        else
            return ColorUtils.lerpColor(
                temperatureEmissionGradient[index],
                temperatureEmissionGradient[index + 1],
                remainder
            )
    }

    private val temperatureEmissionGradient = arrayOf(
        Color.fromRGB(31, 26, 25),
        Color.fromRGB(67, 58, 53),
        Color.fromRGB(102, 74, 66),
        Color.fromRGB(157, 108, 92),
        Color.fromRGB(194, 133, 112),
        Color.fromRGB(223, 135, 73),
        Color.fromRGB(238, 160, 101),
        Color.fromRGB(241, 183, 120),
        Color.fromRGB(245, 209, 142),
        Color.fromRGB(255, 243, 170)
    )


}