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
import com.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Location

class NuclearFogVFX(private val nuclearComponent: NuclearComponent, private val position: Location) : AnimatedEffect(.5, 3600) {
    private val nuclearFog: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3(14.0, 14.0, 14.0))
                .displacement(Vector3(.0, .5, .0)),
            SphereBuilder()
                .withRadiusXZ(200.0)
                .withRadiusY(1.0),
            position
        ).apply {
            snapToFloor(250.0, 150.0, position)
        },
        TemperatureComponent(temperatureCoolingRate = 200.0)
    ).apply {
        applyRadialVelocityFromCenter(Vector3(.5, 0.0, .5))
        emitRate(40)
    }

    init {
        effectComponents = mutableListOf(nuclearFog)
    }

    override fun animate(delta: Double) {

    }

}