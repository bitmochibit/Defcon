/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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
package me.mochibit.defcon.effects.explosion.generic

import me.mochibit.defcon.utils.Logger.err
import me.mochibit.defcon.effects.AnimatedEffect
import me.mochibit.defcon.effects.ParticleComponent
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.emitter.RingSurfaceShape
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a shockwave effect that expands outward with decreasing particle density
 * @param center The center location of the shockwave
 * @param shockwaveRadius The maximum radius the shockwave will reach
 * @param initialRadius The starting radius of the shockwave
 * @param expansionSpeed How fast the shockwave expands per second
 * @param duration How long the effect lasts (defaults to time needed to reach max radius)
 * @param initialDensityFactor Base particle density factor
 * @param densityDecayFactor How quickly density decreases as radius increases (higher = faster decay)
 */
class ShockwaveEffect(
    private val center: Location,
    private val shockwaveRadius: Int,
    private val initialRadius: Int = 0,
    private val expansionSpeed: Float = 50f,
    duration: Duration = ((shockwaveRadius - initialRadius) / expansionSpeed).roundToInt().seconds,
    private val initialDensityFactor: Float = 1f,
    private val densityDecayFactor: Float = 0.5f,
) : AnimatedEffect(maxAliveDuration = duration) {

    private val shockwave: ParticleComponent<RingSurfaceShape>
    private val shockwaveShape: RingSurfaceShape

    // Cache the last radius to avoid unnecessary updates
    private var lastUpdatedRadius: Float = initialRadius.toFloat()

    init {
        val emitter = ParticleEmitter(
            center,
            1000.0,
            emitterShape = RingSurfaceShape(
                ringRadius = initialRadius.toFloat(),
                tubeRadius = 0.8f, // Slightly reduced for better performance
            ),
//            shapeMutator = SimpleFloorSnap(center),
            maxParticlesInitial = 10000,
        )

        shockwave = ParticleComponent(emitter).addSpawnableParticle(
            ExplosionDustParticle().apply {
                defaultColor = Color.GRAY
                scale(30f, 20f, 30f)
                maxLife = 20
            }
        ).applyRadialVelocityFromCenter(
            Vector3f(10f, 0f, 10f)
        )

        shockwaveShape = emitter.emitterShape
        effectComponents.add(shockwave)
    }


    override fun animate(delta: Float) {
        try {
            // Check if we've reached maximum radius
            if (shockwaveShape.ringRadius >= shockwaveRadius) {
                return
            }

            // Calculate radius increase for this frame
            val shockwaveDelta = delta * expansionSpeed

            // Calculate new radius with bounds checking
            val newRadius = min(
                shockwaveRadius.toFloat(),
                shockwaveShape.ringRadius + shockwaveDelta
            )

            // Update radius
            shockwaveShape.ringRadius = newRadius
        } catch (e: Exception) {
            err("Error in shockwave animation: ${e.message ?: "Unknown error"}")
        }
    }
}