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

package com.mochibit.defcon.effects.nuclear

import com.mochibit.defcon.effects.AnimatedEffect
import com.mochibit.defcon.effects.ParticleComponent
import com.mochibit.defcon.effects.TemperatureComponent
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.ExplosionDustParticle
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import com.mochibit.defcon.vertexgeometry.shapes.CylinderBuilder
import com.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Location

class NuclearExplosionVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect(3600) {
    private val maxHeight = 250.0
    private var currentHeight = 0.0
    private var riseSpeed = 5.0
    private val visibleWhenLessThanCurrentHeight = { value: Double -> value < currentHeight - 5 }
    private val visibleAfterACertainHeight = { value: Double -> value >= 120 }

    private val coreCloud: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .velocity(Vector3(0.0, 1.5, 0.0)),
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
                .scale(Vector3(12.0, 12.0, 12.0))
                .velocity(Vector3(0.0, 1.3, 0.0)),
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
                .scale(Vector3(14.0, 14.0, 14.0))
                .velocity(Vector3(0.0, 1.2, 0.0)),
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
                .scale(Vector3(15.0, 15.0, 15.0))
                .velocity(Vector3(0.0, -.8, 0.0)),
            SphereBuilder()
                .hollow(true)
                .skipRadiusXZ(70.0)
                .withRadiusXZ(90.0)
                .withRadiusY(60.0),
            center
        ),
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).translate(Vector3(0.0, -5.0, 0.0)).emitRate(17)

    private val coreNeck: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .velocity(Vector3(0.0, -1.0, 0.0)),
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
    ).translate(Vector3(0.0, -30.0, 0.0)).emitRate(12)


    private val neckCone: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle(),
            SphereBuilder()
                .hollow(true)
                .withYStart(-15.0)
                .withRadiusXZ(40.0)
                .withRadiusY(70.0),
            center
        ).apply { yPredicate = visibleAfterACertainHeight },
        TemperatureComponent(temperatureCoolingRate = 100.0)
    ).translate(Vector3(0.0, -90.0, 0.0)).emitRate(15).visible(false).setVisibilityAfterDelay(true, 20*15)
        .applyRadialVelocityFromCenter(Vector3(3.0, -1.0, 3.0))



    private val stem: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3(11.0, 11.0, 11.0))
                .velocity(Vector3(0.0, 2.0, 0.0)),
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

    override fun animate(delta: Double) {
        processRise(delta)
    }


    private fun processRise(delta: Double) {
        if (currentHeight > maxHeight) return
        val deltaMovement = riseSpeed * delta
        // Elevate the sphere using transform translation
        coreCloud.translate(Vector3(0.0, deltaMovement, 0.0))
        coreNeck.translate(Vector3(0.0, deltaMovement, 0.0))
        secondaryCloud.translate(Vector3(0.0, deltaMovement, 0.0))
        tertiaryCloud.translate(Vector3(0.0, deltaMovement, 0.0))
        quaterniaryCloud.translate(Vector3(0.0, deltaMovement, 0.0))
        neckCone.translate(Vector3(0.0, deltaMovement, 0.0))
        currentHeight += deltaMovement
    }

}