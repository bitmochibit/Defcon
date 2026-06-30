/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2025 mochibit.
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

package me.mochibit.defcon.effects.explosion.nuclear

import me.mochibit.defcon.effects.AnimatedEffect
import me.mochibit.defcon.effects.ParticleComponent
import me.mochibit.defcon.effects.TemperatureComponent
import me.mochibit.defcon.explosions.ExplosionComponent
import me.mochibit.defcon.particles.emitter.CylinderShape
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.emitter.SphereShape
import me.mochibit.defcon.particles.emitter.SphereSurfaceShape
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class NuclearExplosionVFX(
    private val nuclearComponent: ExplosionComponent,
    val center: Location,
    duration: Duration = 2.minutes,
) :
    AnimatedEffect(maxAliveDuration = duration) {
    private val riseSpeed = 4.0f

    // Reduced maximum height the explosion can reach
    private val maxHeight = 350.0

    // Component height offsets, now representing initial local positions
    private val coreOffset = -25f
    private val secondaryOffset = -25.0f
    private val tertiaryOffset = -30.0f
    private val quaternaryOffset = -15.0f
    private val neckSkirtOffset = -75.0f

    // Track current progress of the rise animation (0.0 to 1.0)
    private var riseProgress = 0.0f

    // Track the current height for stem calculation
    private var currentHeight = 0.0f

    // Position tracking for components
    private val componentInitialPositions = mutableMapOf<ParticleComponent<*>, Vector3f>()


    private val coreCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereShape(
                xzRadius = 45.0f,
                yRadius = 50.0f,
                minY = 0.0,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 15.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(30.0f, 30.0f, 30.0f)
                initialVelocity(0.0, -1.5, 0.0)
            }
    )

    private val secondaryCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 50.0f,
                yRadius = 50.0f,
                minY = 0.0,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 40.0)
    ).addSpawnableParticle(
        ExplosionDustParticle().apply {
            scale(45.0f, 45.0f, 45.0f)
            initialVelocity(0.0, -2.0, 0.0)
        }
    )

    private val tertiaryCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 70.0f,
                yRadius = 70.0f,
                minY = 0.0
            ),
        ),
        TemperatureComponent(baseCoolingRate = 45.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(50.0f, 50.0f, 50.0f)
                initialVelocity(0.0, 3.5, 0.0)
            }
    )

    private val quaternaryCloud = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereShape(
                xzRadius = 90.0f,
                yRadius = 60.0f,
                minY = 20.0,
                excludedXZRadius = 70.0,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 50.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(55.0f, 55.0f, 55.0f)
                initialVelocity(0.0, -5.5, 0.0)
            }
    )

    private val neckSkirt = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 45.0f,
                yRadius = 65.0f,
                minY = 65.0,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 25.0, maxTemperature = 5000.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                defaultColor = Color.GRAY
                initialVelocity(0.0, -1.0, 0.0)
                scale(30.0f, 30.0f, 30.0f)
            }
    )
        .apply {
            visible = false
        }
        .setVisibilityAfterDelay(true, 40.seconds)
        .applyRadialVelocityFromCenter(Vector3f(5.0f, 0f, 5.0f))

    private val stem = ParticleComponent(
        ParticleEmitter(
            center, 8000.0,
            emitterShape = CylinderShape(
                radiusX = 15.0f,
                radiusZ = 15.0f,
                height = 1f,
            ),
        ),
        TemperatureComponent(baseCoolingRate = 20.0, turbulenceFactor = 0.05)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .apply {
                scale(40.0f, 40.0f, 40.0f)
                initialVelocity(0.0, 2.0, 0.0)
            }
    ).apply {
        positionBasedColoring = true
    }

    private val stemShape = stem.shape
    private val neckConeShape = neckSkirt.shape


    init {
        componentInitialPositions[coreCloud] = Vector3f(0f, coreOffset, 0f)
        componentInitialPositions[secondaryCloud] = Vector3f(0f, secondaryOffset, 0f)
        componentInitialPositions[tertiaryCloud] = Vector3f(0f, tertiaryOffset, 0f)
        componentInitialPositions[quaternaryCloud] = Vector3f(0f, quaternaryOffset, 0f)
        componentInitialPositions[neckSkirt] = Vector3f(0f, neckSkirtOffset, 0f)

        // Apply initial positions
        componentInitialPositions.forEach { (component, position) ->
            component.translate(position)
        }

        effectComponents.addAll(
            listOf(
                coreCloud,
                secondaryCloud,
                tertiaryCloud,
                quaternaryCloud,
                neckSkirt,
                stem
            )
        )
    }

    override fun animate(delta: Float) {
        // Calculate movement for this frame based on single rise speed
        val deltaMovement = riseSpeed * delta

        // Update rise progress (capped at 1.0)
        riseProgress = (riseProgress + (deltaMovement / maxHeight.toFloat())).coerceAtMost(1.0f)

        // Calculate current height based on progress
        val newHeight = riseProgress * maxHeight.toFloat()

        // Calculate the delta height for this frame
        val heightDelta = newHeight - currentHeight

        // Update current height for next frame
        currentHeight = newHeight

        // Move each component by the delta height
        moveComponent(coreCloud, heightDelta)
        moveComponent(secondaryCloud, heightDelta)
        moveComponent(tertiaryCloud, heightDelta)
        moveComponent(quaternaryCloud, heightDelta)
        moveComponent(neckSkirt, heightDelta)

        if (neckSkirt.visible && neckConeShape.minY > 0) {
            neckConeShape.minY -= (riseSpeed * 2 * delta)
        }

        // Adjust stem height to match current explosion height
        stemShape.height = (currentHeight - 40).coerceAtLeast(1f)
    }

    /**
     * Moves a component by the specified delta height
     */
    private fun moveComponent(
        component: ParticleComponent<*>,
        heightDelta: Float
    ) {
        // Apply only the delta movement for this frame
        component.translate(Vector3f(0.0f, heightDelta, 0.0f))
    }
}