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

import com.mochibit.defcon.customassets.items.definitions.ExplosionDust
import com.mochibit.defcon.effects.BaseComponent
import com.mochibit.defcon.effects.CompoundComponent
import com.mochibit.defcon.effects.TemperatureComponent
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.particles.ExplosionDustParticle
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import com.mochibit.defcon.vertexgeometry.shapes.CylinderBuilder
import com.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Location

class NuclearMushroom(nuclearComponent: NuclearComponent, center: Location) : CompoundComponent() {

    val coreCloud = TemperatureComponent(
        particleShape = ParticleShape(
            SphereBuilder()
                .withRadiusXZ(30.0)
                .withRadiusY( 50.0)
                .withYStart(0.0)
            ,
            ExplosionDustParticle(),
            center
        )
    )

    val secondaryCloud = TemperatureComponent(
        particleShape = ParticleShape(
            SphereBuilder()
                .withRadiusXZ(50.0)
                .withRadiusY( 55.0)
                .skipRadiusXZ(20.0)
                .withYStart(-10.0)
            ,
            ExplosionDustParticle(),
            center
        )
    )

    val tertiarySpheroid = TemperatureComponent(
        ParticleShape(
            SphereBuilder()
                .withRadiusXZ(60.0)
                .withRadiusY( 50.0)
                .skipRadiusXZ(40.0)
                .withYStart(-15.0)
            ,
            ExplosionDustParticle(),
            center
        )
    )

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
    )

    val stem = TemperatureComponent(
        ParticleShape(
            CylinderBuilder()
                .withHeight(90.0)
                .withRadiusX(15.0)
                .withRadiusZ(15.0)
                .withRate(30.0)
                .hollow(false),
            ExplosionDustParticle(),
            center
        )
    )

    val foot = TemperatureComponent(
        ParticleShape(
            CylinderBuilder()
                .withHeight(10.0)
                .withRadiusX(4.0)
                .withRadiusZ(4.0)
                .withRate(16.0)
                .hollow(true),
            ExplosionDustParticle(),
            center
        )
    )

    val footSecondary = TemperatureComponent(
        ParticleShape(
            CylinderBuilder()
                .withHeight(1.0)
                .withRadiusX(60.0)
                .withRadiusZ(60.0)
                .withRate(30.0),
            ExplosionDustParticle(),
            center
        )
    )

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
        components = mutableListOf(
            coreCloud,
            secondaryCloud,
            tertiarySpheroid,
            primaryNeck,
            stem,
            foot,
            footSecondary,
            condensationCloud
        )

    }
}