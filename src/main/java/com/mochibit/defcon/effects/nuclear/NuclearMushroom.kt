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

import com.mochibit.defcon.effects.BaseComponent
import com.mochibit.defcon.effects.CompoundComponent
import com.mochibit.defcon.effects.TemperatureComponent
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.ExplosionDustParticle
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import com.mochibit.defcon.vertexgeometry.shapes.CylinderBuilder
import com.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Location

class NuclearMushroom(nuclearComponent: NuclearComponent, center: Location) : CompoundComponent() {
    val maxHeight = 250.0
    var currentHeight = 0.0
    var riseSpeed = 5.0

    val coreCloud = TemperatureComponent(
        particleShape = ParticleShape(
            SphereBuilder()
                .withRadiusXZ(30.0)
                .withRadiusY(50.0),
            ExplosionDustParticle()
                .velocity(Vector3(0.0, 1.5, 0.0)),
            center
        )
    ).applyHeatedSmokeColor().apply { temperatureCoolingRate = 100.0 }


    val secondaryCloud = TemperatureComponent(
        particleShape = ParticleShape(
            SphereBuilder()
                .skipRadiusXZ(30.0)
                .withRadiusXZ(50.0)
                .withRadiusY(50.0),
            ExplosionDustParticle()
                .scale(Vector3(12.0,12.0,12.0))
                .velocity(Vector3(0.0, 1.3, 0.0)),
            center
        )
    ).applyHeatedSmokeColor().apply { temperatureCoolingRate = 105.0 }

    val tertiaryCloud = TemperatureComponent(
        particleShape = ParticleShape(
            SphereBuilder()
                .skipRadiusXZ(50.0)
                .withRadiusXZ(70.0)
                .withRadiusY(70.0),
            ExplosionDustParticle()
                .scale(Vector3(14.0,14.0,14.0))
                .velocity(Vector3(0.0, 1.2, 0.0)),
            center
        )
    ).applyHeatedSmokeColor().apply { temperatureCoolingRate = 110.0}

    val quaterniaryCloud = TemperatureComponent(
        ParticleShape(
            SphereBuilder()
                .skipRadiusXZ(70.0)
                .withRadiusXZ(90.0)
                .withRadiusY(60.0),
            ExplosionDustParticle()
                .scale(Vector3(15.0,15.0,15.0))
                .velocity(Vector3(0.0, -.8, 0.0)),
            center
        )
    ).applyHeatedSmokeColor().apply {
        transform = transform.translated(Vector3(0.0, -5.0, 0.0))
        temperatureCoolingRate = 115.0
        emitRate(15)
    }

    val coreNeck = TemperatureComponent(
        particleShape = ParticleShape(
            CylinderBuilder()
                .withHeight(60.0)
                .withRadiusX(30.0)
                .withRadiusZ(30.0)
                .withRate(20.0)
                .withHeightRate(1.0)
                .hollow(false),
            ExplosionDustParticle()
                .velocity(Vector3(0.0, -1.0, 0.0)),
            center
        ).apply {
            transform = transform.translated(Vector3(0.0, -30.0, 0.0))
        }
    ).applyHeatedSmokeColor().apply { temperatureCoolingRate = 95.0}

    val primaryNeck = TemperatureComponent(
        ParticleShape(
            CylinderBuilder()
                .withHeight(1.0)
                .withRadiusX(14.0)
                .withRadiusZ(14.0)
                .withRate(30.0)
                .hollow(true),
            ExplosionDustParticle(),
            center
        )
    ).applyHeatedSmokeColor()

    val stem = TemperatureComponent(
        ParticleShape(
            CylinderBuilder()
                .withHeight(maxHeight)
                .withRadiusX(15.0)
                .withRadiusZ(15.0)
                .withRate(30.0)
                .hollow(false),
            ExplosionDustParticle()
                .scale(Vector3(11.0,11.0,11.0))
                .velocity(Vector3(0.0, 2.0, 0.0)),
            center
        ).heightPredicate(this::visibleWhenLessThanCurrentHeight)
    ).applyHeatedSmokeColor().apply { temperatureCoolingRate = 140.0 }

    val foot = TemperatureComponent(
        ParticleShape(
            CylinderBuilder()
                .withHeight(15.0)
                .withRadiusX(30.0)
                .withRadiusZ(30.0)
                .withRate(32.0)
                .hollow(false),
            ExplosionDustParticle()
                .velocity(Vector3(0.0, 1.0, 0.0))
            ,
            center
        )
    ).applyHeatedSmokeColor().apply { temperatureCoolingRate = 175.0 }

    val nuclearFog = TemperatureComponent(
        ParticleShape(
            SphereBuilder()
                .withRadiusXZ(200.0)
                .withRadiusY(1.0),
            ExplosionDustParticle()
                .scale(Vector3(14.0,14.0,14.0))
                .displacement(Vector3(.0, .5, .0)),
            center
        ).snapToFloor(10.0, 50.0)
    ).apply {
        applyHeatedSmokeColor()
        applyRadialVelocityFromCenter(Vector3(.5, 0.0, .5))
        temperatureCoolingRate = 200.0
        emitRate(25)
    }

    val condensationCloud = BaseComponent(
        ParticleShape(
            SphereBuilder()
                .withRadiusXZ(20.0)
                .withRadiusY(20.0)
                .withDensity(1.0)
                .withYStart(-10.0)
                .withYEnd(20.0)
                .hollow(true)
                .ignoreBottomSurface(true),
            ExplosionDustParticle(),
            center
        )
    )

    init {
        components = arrayOf(
            coreCloud,
            coreNeck,
            secondaryCloud,
            tertiaryCloud,
            quaterniaryCloud,
            //primaryNeck,
            stem,
            foot,
            nuclearFog,
            //condensationCloud
        )
    }

    fun processEffects(delta: Double, lifeTime: Double) {
        processRise(delta)
        coolDown(delta)
    }

    private fun coolDown(delta: Double) {
        coreCloud.coolDown(delta)
        coreNeck.coolDown(delta)
        secondaryCloud.coolDown(delta)
        tertiaryCloud.coolDown(delta)
        quaterniaryCloud.coolDown(delta)
        //primaryNeck.temperature -= delta;
        stem.coolDown(delta)
        foot.coolDown(delta)
        nuclearFog.coolDown(delta)
        //condensationCloud.temperature -= delta;
    }

    private fun processRise(delta: Double) {
        if (currentHeight > maxHeight) return;
        val deltaMovement = riseSpeed * delta;
        // Elevate the sphere using transform translation
        coreCloud.transform = coreCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        coreNeck.transform = coreNeck.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        secondaryCloud.transform = secondaryCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        tertiaryCloud.transform = tertiaryCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        quaterniaryCloud.transform = quaterniaryCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        //primaryNeck.transform = primaryNeck.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        //condensationCloud.transform = condensationCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        currentHeight += deltaMovement;
    }

    fun visibleWhenLessThanCurrentHeight(value: Double): Boolean {
        return value < currentHeight - 5;
    }

}