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

package com.mochibit.defcon.particles

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import kotlin.random.Random

abstract class CustomParticle(private val properties: DisplayParticleProperties) : AbstractParticle(properties) {
    var randomizeColorBrightness = true
    final override fun spawn(location: Location) {
        if (properties.color != null) {
            var finalColor = colorSupplier?.invoke(location) ?: properties.color
            if (randomizeColorBrightness)
                finalColor = finalColor?.let { randomizeColorBrightness(it) }
            properties.color = finalColor
        }

        DisplayItemAsyncHandler(location, Bukkit.getOnlinePlayers(), properties)
            .summonWithMetadata()
            .applyVelocity(velocity)

    }

    private fun randomizeColorBrightness(color: Color): Color {
        return if (Random.nextBoolean())
            ColorUtils.darkenColor(color, Random.nextDouble(0.8, 1.0))
        else ColorUtils.lightenColor(color, Random.nextDouble(0.1, 0.2));
    }


}