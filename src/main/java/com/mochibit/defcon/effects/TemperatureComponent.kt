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

    var smokeColor: Color = Color.fromRGB(102,104,102), // #666866
    var baseColor: Color = Color.fromRGB(247, 227, 129) // #F7E381
) : BaseComponent(particleShape) {

    var temperature: Double = maxTemperature
    set(value) {
        field = value.coerceIn(minTemperature, maxTemperature)
    }


    /**
     * The particle slowly cool downs to the minimum temperature, then the color will transition to black smoke
     */
    fun applyHeatedSmokeColor() : TemperatureComponent {
        // Sets the color supplier to apply the temperature emission
        colorSupplier = {
            blackBodyEmission()
        }
        return this
    }

    private fun blackBodyEmission(): Color {
        return if (temperature > minTemperatureEmission) {
            ColorUtils.tempToRGB(temperature.coerceIn(minTemperatureEmission, maxTemperatureEmission))
        } else {
            val ratio = MathFunctions.remap(temperature, minTemperature, maxTemperature, 0.0, 1.0)
            ColorUtils.lerpColor(smokeColor, baseColor, ratio)
        }
    }


}