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

import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import org.bukkit.Color
import org.bukkit.Location

class TemperatureComponent(particleShape: ParticleShape) : BaseComponent(particleShape) {
    var minTemperature = 0.0
    var maxTemperature = 100.0
    var temperature = 0.0
    var minY = 0.0
    var maxY = 0.0
    var transitionProgress = 0.0
    var minimumColor = Color.BLACK
    var baseColor = Color.BLACK

    fun getColorHeightSupplier(loc: Location): () -> Color {
        val height = loc.y
        return {
            applyTemperatureEmission(height)
        }
    }

    private fun applyTemperatureEmission(height: Double): Color {
        return if (temperature > minTemperature) {
            ColorUtils.tempToRGB(temperature)
        } else {
            // Remap the height to a value between 0 and 1 using the minY and maxY and use the transitionProgress to control how much the height affects the color
            val ratio = MathFunctions.remap(height, minY, maxY, transitionProgress, 1.0) * transitionProgress
            ColorUtils.lerpColor(minimumColor, baseColor, ratio)
        }
    }


}