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

package me.mochibit.defcon.effects.nuclear

import me.mochibit.defcon.effects.AnimatedEffect
import me.mochibit.defcon.effects.ParticleComponent
import me.mochibit.defcon.effects.TemperatureComponent
import me.mochibit.defcon.explosions.NuclearComponent
import me.mochibit.defcon.particles.ParticleEmitter
import me.mochibit.defcon.particles.emitter.CylinderShape
import me.mochibit.defcon.particles.emitter.SphereShape
import me.mochibit.defcon.particles.emitter.SphereSurfaceShape
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Location
import org.joml.Vector3f

class NuclearExplosionVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect(3600) {
    private val maxHeight = 250.0
    private var currentHeight = 0.0f
    private var riseSpeed = 5.0f

    private val coreCloud: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = SphereShape(
                xzRadius = 30.0f,
                yRadius = 50.0f
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 35.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(30.0f, 30.0f, 30.0f))
            .velocity(Vector3f(0.0f, .5f, 0.0f))
            .damping(
                Vector3f(0.0f, 0.1f, 0.0f)
            ), true
    )


    private val secondaryCloud: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 50.0f,
                yRadius = 50.0f,
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 105.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(45.0f, 45.0f, 45.0f))
            .velocity(Vector3f(0.0f, .3f, 0.0f))
            .damping(Vector3f(0.0f, 0.1f, 0.0f)), true
    )

    private val tertiaryCloud: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 70.0f,
                yRadius = 70.0f,
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 200.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(50.0f, 50.0f, 50.0f))
            .velocity(Vector3f(0.0f, .2f, 0.0f))
            .damping(Vector3f(0.0f, 0.1f, 0.0f)),
        true
    )

    private val quaterniaryCloud: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = SphereSurfaceShape(
                xzRadius = 90.0f,
                yRadius = 60.0f,
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(55.0f, 55.0f, 55.0f))
            .velocity(Vector3f(0.0f, -.8f, 0.0f))
            .damping(Vector3f(0.0f, 0.1f, 0.0f)),
        true
    )
        .translate(Vector3f(0.0f, -5.0f, 0.0f))

    private val coreNeck: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = CylinderShape(
                radiusX = 30.0f,
                radiusZ = 30.0f,
                height = 60.0f,
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 40.0)
    ).addSpawnableParticle(
        ExplosionDustParticle().velocity(Vector3f(0.0f, -1.0f, 0.0f))
    )
        .translate(Vector3f(0.0f, -30.0f, 0.0f))

    private val neckCone: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = SphereShape(
                xzRadius = 40.0f,
                yRadius = 70.0f,
                minY = -15.0
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 100.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
    )
        .apply {
            visible = false
        }
        .translate(Vector3f(0.0f, -90.0f, 0.0f))
        .setVisibilityAfterDelay(true, 20 * 50)
        .applyRadialVelocityFromCenter(Vector3f(5.0f, -3.0f, 5.0f))

    private val stem: ParticleComponent = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = CylinderShape(
                radiusX = 15.0f,
                radiusZ = 15.0f,
                height = 1f,
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 140.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(40.0f, 40.0f, 40.0f))
            .velocity(Vector3f(0.0f, 1.0f, 0.0f))
    )

    private val stemShape = stem.shape as CylinderShape
    private val neckConeShape = neckCone.shape as SphereShape


    init {
        effectComponents.addAll(
            listOf(
                coreCloud,
                coreNeck,
                secondaryCloud,
                tertiaryCloud,
                quaterniaryCloud,
                neckCone,
                stem
            )
        )

    }

    override fun animate(delta: Float) {
        processRise(delta)
    }


    private fun processRise(delta: Float) {
        if (currentHeight > maxHeight) return
        val deltaMovement = riseSpeed * delta
        val movementVector = Vector3f(0.0f, deltaMovement, 0.0f)
        // Elevate the sphere using transform translation
        coreCloud.translate(movementVector)
        coreNeck.translate(movementVector)
        secondaryCloud.translate(movementVector)
        tertiaryCloud.translate(movementVector)
        quaterniaryCloud.translate(movementVector)
        neckCone.translate(movementVector)
        currentHeight += deltaMovement

        // Gradually increase the displayed height of the cone to simulate the nuke skirt
        if (neckCone.visible)
            neckConeShape.maxY += deltaMovement/5

        stemShape.height = (currentHeight - 70)
    }

}