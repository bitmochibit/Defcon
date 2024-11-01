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
import me.mochibit.defcon.explosions.Shockwave
import me.mochibit.defcon.extensions.toVector3d
import me.mochibit.defcon.particles.ParticleEmitter
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import me.mochibit.defcon.vertexgeometry.particle.ParticleShape
import me.mochibit.defcon.vertexgeometry.shapes.CylinderBuilder
import me.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.joml.Vector3f

class NuclearFogVFX(private val nuclearComponent: NuclearComponent, private val position: Location) :
    AnimatedEffect(3600) {

    private val snapFromVector = position.toVector3d()
    private val fogEmitter = ParticleEmitter(position, 3000.0)
    var chunkSnapshotCache: MutableMap<Pair<Int, Int>, ChunkSnapshot> = mutableMapOf()


    private val nuclearFog: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3f(60.0f, 60.0f, 60.0f))
                .displacement(Vector3f(.0f, .5f, .0f)),
            fogEmitter,
            SphereBuilder()
                .withRadiusXZ(160.0)
                .skipRadiusXZ(80.0)
                .withRadiusY(1.0),
            position
        ).apply {
            snapToFloor(250.0, 150.0, snapFromVector, false, chunkSnapshotCache)
        },
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).apply {
        applyRadialVelocityFromCenter(Vector3f(.5f, 0.0f, .5f))
    }.emitRate(10)

    private val footSustain: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .scale(Vector3f(50.0f, 50.0f, 50.0f))
                .displacement(Vector3f(.0f, .5f, .0f)),
            fogEmitter,
            SphereBuilder()
                .withRadiusXZ(80.0)
                .withRadiusY(1.0),
            position
        ).apply {
            snapToFloor(250.0, 150.0, snapFromVector, false, chunkSnapshotCache)
        },
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).apply {
        applyRadialVelocityFromCenter(Vector3f(.5f, 0.0f, .5f))
    }.emitRate(20)

    private val foot: ParticleComponent = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle()
                .velocity(Vector3f(0.0f, 1.0f, 0.0f)),
            fogEmitter,
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


    init {
        effectComponents = mutableListOf(
            nuclearFog,
            footSustain,
            foot
        )
    }

    override fun animate(delta: Float) {

    }

}