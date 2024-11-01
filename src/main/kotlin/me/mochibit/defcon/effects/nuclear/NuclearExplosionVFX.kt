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
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import me.mochibit.defcon.vertexgeometry.particle.ParticleShape
import me.mochibit.defcon.vertexgeometry.shapes.CylinderBuilder
import me.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Location
import org.joml.Vector3f

class NuclearExplosionVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect(3600) {
    private val maxHeight = 250.0
    private var currentHeight = 0.0f
    private var riseSpeed = 5.0f
    private val visibleWhenLessThanCurrentHeight = { value: Double -> value < currentHeight - 5 }
    private val visibleAfterACertainHeight = { value: Double -> value >= 120 }

    private val mushroomCloudEmitter = ParticleEmitter(center, 3000.0)

    private val coreCloud: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .velocity(Vector3f(0.0f, .5f, 0.0f))
                .damping(Vector3f(0.0f, 0.1f, 0.0f)),
            mushroomCloudEmitter,
            SphereBuilder()
                .withRadiusXZ(30.0)
                .hollow(true)
                .withRadiusY(50.0),
            center
        ),
        TemperatureComponent(temperatureCoolingRate = 35.0)
    ).emitRate(10)
    private val secondaryCloud: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3f(45.0f, 45.0f, 45.0f))
                .velocity(Vector3f(0.0f, .3f, 0.0f))
                .damping(Vector3f(0.0f, 0.1f, 0.0f)),
            mushroomCloudEmitter,
            SphereBuilder()
                .skipRadiusXZ(30.0)
                .hollow(true)
                .withRadiusXZ(50.0)
                .withRadiusY(50.0),
            center
        ),
        TemperatureComponent(temperatureCoolingRate = 105.0)
    ).emitRate(13)
    private val tertiaryCloud: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3f(50.0f, 50.0f, 50.0f))
                .velocity(Vector3f(0.0f, .2f, 0.0f))
                .damping(Vector3f(0.0f, 0.1f, 0.0f)),
            mushroomCloudEmitter,
            SphereBuilder()
                .skipRadiusXZ(50.0)
                .hollow(true)
                .withRadiusXZ(70.0)
                .withRadiusY(70.0),
            center
        ),
        TemperatureComponent(temperatureCoolingRate = 200.0)
    ).emitRate(15)
    private val quaterniaryCloud: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3f(55.0f, 55.0f, 55.0f))
                .velocity(Vector3f(0.0f, -.8f, 0.0f))
                .damping(Vector3f(0.0f, 0.1f, 0.0f)),
            mushroomCloudEmitter,
            SphereBuilder()
                .hollow(true)
                .skipRadiusXZ(70.0)
                .withRadiusXZ(90.0)
                .withRadiusY(60.0),
            center
        ),
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).translate(Vector3f(0.0f, -5.0f, 0.0f)).emitRate(17)

    private val coreNeck: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .velocity(Vector3f(0.0f, -1.0f, 0.0f)),
            mushroomCloudEmitter,
            CylinderBuilder()
                .hollow(true)
                .withHeight(60.0)
                .withRadiusX(30.0)
                .withRadiusZ(30.0)
                .withRate(20.0)
                .withHeightRate(1.0),
            center
        ),
        TemperatureComponent(temperatureCoolingRate = 40.0)
    ).translate(Vector3f(0.0f, -30.0f, 0.0f)).emitRate(12)


    private val neckCone: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle(),
            mushroomCloudEmitter,
            SphereBuilder()
                .hollow(true)
                .withYStart(-15.0)
                .withRadiusXZ(40.0)
                .withRadiusY(70.0),
            center
        ).apply { yPredicate = visibleAfterACertainHeight },
        TemperatureComponent(temperatureCoolingRate = 100.0)
    ).translate(Vector3f(0.0f, -90.0f, 0.0f)).emitRate(15).visible(false).setVisibilityAfterDelay(true, 20*15)
        .applyRadialVelocityFromCenter(Vector3f(1.0f, -1.0f, 1.0f))



    private val stem: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3f(40.0f, 40.0f, 40.0f))
                .velocity(Vector3f(0.0f, 1.0f, 0.0f)),
            mushroomCloudEmitter,
            CylinderBuilder()
                .withHeight(maxHeight)
                .withRadiusX(15.0)
                .withRadiusZ(15.0)
                .withRate(30.0)
                .hollow(false),
            center
        ).apply{
            yPredicate = visibleWhenLessThanCurrentHeight
        },
        TemperatureComponent(temperatureCoolingRate = 190.0)
    ).emitRate(14)



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
    }

}