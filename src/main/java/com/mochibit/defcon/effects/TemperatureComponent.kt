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

import com.mochibit.defcon.lifecycle.Lifecycled
import com.mochibit.defcon.utils.Gradient
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color

class TemperatureComponent(
    var minTemperatureEmission: Double = 1500.0,
    var maxTemperatureEmission: Double = 4000.0,
    var minTemperature: Double = 40.0,
    var maxTemperature: Double = 6000.0,
    var temperatureCoolingRate: Double = .0
) : ColorSuppliable, Lifecycled {
    override val colorSupplier: () -> Color
        get() = { blackBodyEmission() }

    var temperature: Double = maxTemperature
        set(value) {
            field = value.coerceIn(minTemperature, maxTemperature)
        }

    val color: Color
        get() = blackBodyEmission()

    fun coolDown(delta: Float = 1.0f) {
        temperature -= temperatureCoolingRate * delta
    }


    private fun blackBodyEmission(): Color {
        val ratio = MathFunctions.remap(temperature, minTemperature, maxTemperature, 0.0, 1.0)
        return temperatureEmissionGradient.getColorAt(ratio)
    }

    private val temperatureEmissionGradient = Gradient(
        arrayOf(
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
    )

    override fun start() {}

    override fun update(delta: Float) {
        coolDown(delta)
    }

    override fun stop() {}


}