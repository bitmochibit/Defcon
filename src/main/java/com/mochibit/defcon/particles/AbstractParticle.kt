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
import org.joml.Vector3f
import kotlin.random.Random

abstract class AbstractParticle(val particleProperties: GenericParticleProperties) : PluginParticle {
    var colorSupplier: (() -> Color)? = null; private set
    var locationConsumer: ((location: Location) -> Unit)? = null; private set
    var initialVelocity: Vector3 = Vector3(.0, .0, .0); private set
    var initialDamping: Vector3 = Vector3(.0, .0, .0); private set
    var initialAcceleration: Vector3 = Vector3(.0, .0, .0); private set
    var initialAccelerationTicks = 0; private set
    var randomizeColorBrightness = true; private set
    var randomizeScale: Boolean = false; private set
    var displacement = Vector3(.0, .0, .0); private set
    var colorDarkenFactorMin = 0.8; private set
    var colorDarkenFactorMax = 1.0; private set
    var colorLightenFactorMin = 0.1; private set
    var colorLightenFactorMax = 0.2; private set


    fun accelerationTicks(ticks: Int) = apply { initialAccelerationTicks = ticks }
    fun acceleration(vector3: Vector3) = apply { initialAcceleration = vector3 }
    fun damping(vector3: Vector3) = apply { initialDamping = vector3 }
    fun velocity(vector3: Vector3) = apply { initialVelocity = vector3 }
    fun randomizeColorBrightness(randomize: Boolean) = apply { randomizeColorBrightness = randomize }
    fun displacement(vector3: Vector3) = apply { displacement = vector3 }
    fun colorSupplier(supplier: (() -> Color)?) = apply { colorSupplier = supplier }
    fun locationConsumer(consumer: ((location: Location) -> Unit)?) = apply { locationConsumer = consumer }
    fun scale(scale: Vector3) =
        apply { particleProperties.scale = Vector3f(scale.x.toFloat(), scale.y.toFloat(), scale.z.toFloat()) }

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

    fun colorLightenFactor(min: Double, max: Double) = apply {
        if (min > max) {
            colorLightenFactorMin = max; colorLightenFactorMax = min
        } else {
            colorLightenFactorMin = min; colorLightenFactorMax = max
        }
    }

    protected abstract fun spawnParticle(location: Location)

    override fun spawn(location: Location) {
        locationConsumer?.invoke(location)
        val startingColor = particleProperties.color
        // TODO: Spawning particles with overrides, so we can have randomized stuff more easily
        particleProperties.color = particleProperties.color?.let { color ->
            var finalColor = colorSupplier?.invoke() ?: color
            if (randomizeColorBrightness) {
                finalColor = randomizeColorBrightness(finalColor)
            }
            finalColor
        }
        applyRandomScale()
        applyRandomDisplacement()
        spawnParticle(location)
        particleProperties.color = startingColor
    }

    private fun applyRandomScale() {
        if (!randomizeScale) return

        // From the scale factor, generate a random scale between 1 and the factor value (inclusive)
        val randomizedFactor = Random.nextDouble(0.9, 1.1)
        particleProperties.scale = particleProperties.scale.mul(randomizedFactor.toFloat())

    }

    private fun randomizeColorBrightness(color: Color): Color {
        val factor: Double
        if (Random.nextBoolean()) {
            if (colorDarkenFactorMax == 0.0 && colorDarkenFactorMin == 0.0) return color
            factor = if (colorDarkenFactorMin == colorDarkenFactorMax) {
                colorDarkenFactorMin
            } else {
                Random.nextDouble(colorDarkenFactorMin, colorDarkenFactorMax)
            }
            return ColorUtils.darkenColor(color, factor)
        } else {
            if (colorLightenFactorMax == 0.0 && colorLightenFactorMin == 0.0) return color
            factor = if (colorLightenFactorMin == colorLightenFactorMax) {
                colorLightenFactorMin
            } else {
                Random.nextDouble(colorLightenFactorMin, colorLightenFactorMax)
            }
            return ColorUtils.lightenColor(color, factor)
        }
    }

    private fun applyRandomDisplacement() {
        if (displacement == Vector3(.0, .0, .0)) return
        val (x, y, z) = displacement
        initialVelocity = Vector3(
            initialVelocity.x + if (x != 0.0) randomDisplacement(x) else 0.0,
            initialVelocity.y + if (y != 0.0) randomDisplacement(y) else 0.0,
            initialVelocity.z + if (z != 0.0) randomDisplacement(z) else 0.0
        )
    }

    private fun randomDisplacement(value: Double) =
        if (value > 0) Random.nextDouble(0.0, value) else Random.nextDouble(value, 0.0)
}
