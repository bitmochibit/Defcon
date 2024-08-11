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

import com.mochibit.defcon.Defcon.Companion.Logger.info
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

class NuclearFogVFX(private val nuclearComponent: NuclearComponent, private val position: Location) :
    AnimatedEffect( 3600) {

    private val nuclearFog: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3(14.0, 14.0, 14.0))
                .displacement(Vector3(.0, .5, .0)),
            SphereBuilder()
                .withRadiusXZ(160.0)
                .skipRadiusXZ(80.0)
                .withRadiusY(1.0),
            position
        ).apply {
            snapToFloor(250.0, 150.0, position, false)
        },
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).apply {
        applyRadialVelocityFromCenter(Vector3(.5, 0.0, .5))
    }.emitRate(10)

    private val footSustain: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3(14.0, 14.0, 14.0))
                .displacement(Vector3(.0, .5, .0)),
            SphereBuilder()
                .withRadiusXZ(80.0)
                .withRadiusY(1.0),
            position
        ).apply {
            snapToFloor(250.0, 150.0, position, false)
        },
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).apply {
        applyRadialVelocityFromCenter(Vector3(.5, 0.0, .5))
    }.emitRate(20)

    private val foot: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .velocity(Vector3(0.0, 1.0, 0.0)),
            CylinderBuilder()
                .withHeight(15.0)
                .withRadiusX(30.0)
                .withRadiusZ(30.0)
                .withRate(32.0)
                .hollow(false),
            position
        ),
        TemperatureComponent(temperatureCoolingRate = 280.0)
    ).emitRate(5)

    private val onLoadListeners = mutableListOf<() -> Unit>()
    fun onLoad(onLoad: () -> Unit) = apply {
        onLoadListeners.add(onLoad)
    }

    var loadedFog = false
    var loadedFootSustain = false

    private fun loadCheck() {
        if (loadedFog && loadedFootSustain) {
            onLoadListeners.forEach { it() }
        }
    }

    init {
        nuclearFog.onLoad {
            loadedFog = true
            loadCheck()
        }
        footSustain.onLoad {
            loadedFootSustain = true
            loadCheck()
        }

        effectComponents = mutableListOf(
            nuclearFog,
            footSustain,
            foot
        )
    }

    override fun animate(delta: Double) {

    }

}