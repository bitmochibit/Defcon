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

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.ColorUtils
import org.bukkit.Color
import org.bukkit.Location
import kotlin.random.Random

abstract class AbstractParticle(val particleProperties: GenericParticleProperties) : PluginParticle {
    var colorSupplier: ((location: Location) -> Color)? = null; private set
    var locationConsumer: ((location: Location) -> Unit)? = null; private set
    var initialVelocity: Vector3 = Vector3(.0, .0, .0); private set
    var initialDamping: Vector3 = Vector3(.0, .0, .0); private set
    var initialAcceleration: Vector3 = Vector3(.0, .0, .0); private set
    var initialAccelerationTicks = 0; private set
    var randomizeColorBrightness = true; private set
    var displacement = Vector3(.0, .0, .0); private set
    fun accelerationTicks(ticks: Int) = apply { initialAccelerationTicks = ticks }
    fun acceleration(vector3: Vector3) = apply { initialAcceleration = vector3 }
    fun damping(vector3: Vector3) = apply { initialDamping = vector3 }
    fun velocity(vector3: Vector3) = apply { initialVelocity = vector3 }
    fun randomizeColorBrightness(randomize: Boolean) = apply { randomizeColorBrightness = randomize }
    fun displacement(vector3: Vector3) = apply { displacement = vector3 }
    fun colorSupplier(supplier: ((location: Location) -> Color)? ) = apply { colorSupplier = supplier }
    fun locationConsumer(consumer: ((location: Location) -> Unit)? ) = apply { locationConsumer = consumer }

    protected abstract fun spawnParticle(location: Location)

    override fun spawn(location: Location) {
        locationConsumer?.invoke(location)
        if (particleProperties.color != null) {
            var finalColor = colorSupplier?.invoke(location) ?: particleProperties.color
            if (randomizeColorBrightness)
                finalColor = finalColor?.let { randomizeColorBrightness(it) }
            particleProperties.color = finalColor
        }
        applyRandomDisplacement(displacement)
        spawnParticle(location)
    }

    private fun randomizeColorBrightness(color: Color): Color {
        return if (Random.nextBoolean())
            ColorUtils.darkenColor(color, Random.nextDouble(0.8, 1.0))
        else ColorUtils.lightenColor(color, Random.nextDouble(0.1, 0.2))
    }

    private fun applyRandomDisplacement(displacement : Vector3 = Vector3(1.0,1.0,1.0)) {
        val x = if (displacement.x > 0.0) Random.nextDouble(-displacement.x, displacement.x) else 0.0
        val y = if (displacement.y > 0.0) Random.nextDouble(-displacement.y, displacement.y) else 0.0
        val z = if (displacement.z > 0.0) Random.nextDouble(-displacement.z, displacement.z) else 0.0
        // Randomize the velocity in a random direction
        initialVelocity = Vector3(
            initialVelocity.x + x,
            initialVelocity.y + y,
            initialVelocity.z + z
        )
    }
}