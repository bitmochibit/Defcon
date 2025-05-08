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

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger.err
import me.mochibit.defcon.effects.AnimatedEffect
import me.mochibit.defcon.effects.ParticleComponent
import me.mochibit.defcon.extensions.toTicks
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.emitter.RingSurfaceShape
import me.mochibit.defcon.particles.mutators.SimpleFloorSnap
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ShockwaveEffect(
    private val center: Location,
    private val shockwaveRadius: Int,
    private val initialRadius: Int = 0,
    private val expansionSpeed: Float = 50f,
    duration: Duration = ((shockwaveRadius - initialRadius) / expansionSpeed).roundToInt().seconds,
) : AnimatedEffect(duration.toTicks()) {

    private val shockwave = ParticleComponent(
        ParticleEmitter(
            center, 1000.0,
            emitterShape = RingSurfaceShape(
                ringRadius = initialRadius.toFloat(),
                tubeRadius = 1f,
            ),
            shapeMutator = SimpleFloorSnap(center.world),
        ),
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .color(Color.WHITE)
            .scale(Vector3f(60.0f, 50.0f, 60.0f))
            .maxLife(20),
    ).applyRadialVelocityFromCenter(
        Vector3f(2f, .0f, 2f)
    )

    private val shockwaveShape = shockwave.shape

    init {
        effectComponents.add(shockwave)
        shockwaveShape.density = 10f
        Defcon.instance.launch(Dispatchers.IO) {
            while (tickAlive / 20 < duration.inWholeSeconds) {
                shockwave.maxParticles += 1000

                delay(1.seconds)
            }
        }
    }

    override fun animate(delta: Float) {
        try {
            // Prevent exceeding maximum radius
            if (shockwaveShape.ringRadius >= shockwaveRadius) {
                return
            }

            val shockwaveDelta = delta * expansionSpeed

            // Calculate new radius with bounds checking
            val newRadius = min(
                shockwaveRadius.toFloat(),
                shockwaveShape.ringRadius + shockwaveDelta
            )
            shockwaveShape.ringRadius = newRadius
        } catch (e: Exception) {
            err("Error in shockwave animation: ${e.message}")
        }
    }
}
