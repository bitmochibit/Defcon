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

package com.mochibit.defcon.particles.templates

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.ParticleAdapter
import com.mochibit.defcon.utils.ColorUtils
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3f
import kotlin.random.Random

abstract class AbstractParticle(val particleProperties: GenericParticleProperties) {
    var colorSupplier: (() -> Color)? = null; private set
    var locationConsumer: ((location: Location) -> Unit)? = null; private set
    var playersSupplier: (() -> Collection<Player>)? = null; private set
    var initialVelocity: Vector3f = Vector3f(0f, 0f, 0f); private set
    var initialDamping: Vector3f = Vector3f(0f, 0f, 0f); private set
    var initialAcceleration: Vector3f = Vector3f(0f, 0f, 0f); private set
    var initialAccelerationTicks = 0; private set
    var randomizeColorBrightness = true; private set
    var randomizeScale: Boolean = false; private set
    var displacement = Vector3f(0f, 0f, 0f); private set
    var colorDarkenFactorMin = 0.8; private set
    var colorDarkenFactorMax = 1.0; private set
    var colorLightenFactorMin = 0.1; private set
    var colorLightenFactorMax = 0.2; private set


    fun accelerationTicks(ticks: Int) = apply { initialAccelerationTicks = ticks }
    fun acceleration(vector3: Vector3f) = apply { initialAcceleration = vector3 }
    fun damping(vector3: Vector3f) = apply { initialDamping = vector3 }
    fun velocity(vector3: Vector3f) = apply { initialVelocity = vector3 }
    fun randomizeColorBrightness(randomize: Boolean) = apply { randomizeColorBrightness = randomize }
    fun displacement(vector3: Vector3f) = apply { displacement = vector3 }
    fun colorSupplier(supplier: (() -> Color)?) = apply { colorSupplier = supplier }
    fun locationConsumer(consumer: ((location: Location) -> Unit)?) = apply { locationConsumer = consumer }
    fun scale(scale: Vector3f) =
        apply { particleProperties.scale = scale }

    fun maxLife(ticks: Long) = apply { particleProperties.maxLife = ticks }
    fun randomizeScale(randomize: Boolean) = apply { randomizeScale = randomize }
    fun color(color: Color) = apply { particleProperties.color = color }
    fun colorDarkenFactor(min: Double, max: Double) = apply {
        if (min > max) {
            colorDarkenFactorMin = max; colorDarkenFactorMax = min
        } else {
            colorDarkenFactorMin = min; colorDarkenFactorMax = max
        }
    }

    fun playersSupplier(supplier: () -> Collection<Player>) = apply { playersSupplier = supplier }

    fun colorLightenFactor(min: Double, max: Double) = apply {
        if (min > max) {
            colorLightenFactorMin = max; colorLightenFactorMax = min
        } else {
            colorLightenFactorMin = min; colorLightenFactorMax = max
        }
    }


    private fun applyRandomScale() {
        if (!randomizeScale) return

        // From the scale factor, generate a random scale between 1 and the factor value (inclusive)
        val randomizedFactor = Random.nextDouble(0.9, 1.1)
        particleProperties.scale = particleProperties.scale.mul(randomizedFactor.toFloat())

    }

    abstract fun getAdapter(): ParticleAdapter


    private fun applyRandomDisplacement() {
        if (displacement.equals(0f, 0f, 0f)) return
        val x = displacement.x
        val y = displacement.y
        val z = displacement.z
        initialVelocity = Vector3f(
            initialVelocity.x + if (x != 0f) randomDisplacement(x) else 0f,
            initialVelocity.y + if (y != 0f) randomDisplacement(y) else 0f,
            initialVelocity.z + if (z != 0f) randomDisplacement(z) else 0f
        )
    }

    private fun randomDisplacement(value: Float) =
        if (value > 0) Random.nextFloat() else Random.nextFloat() * -1
}
