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
import me.mochibit.defcon.extensions.toVector3f
import me.mochibit.defcon.particles.emitter.CylinderShape
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.particles.mutators.FloorSnapper
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import org.bukkit.Location
import org.joml.Vector3f

class NuclearFogVFX(private val nuclearComponent: ExplosionComponent, private val center: Location) :
    AnimatedEffect(3600) {


    private val nuclearFog = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = CylinderShape(
                height = 3f,
                radiusX = 160f,
                radiusZ = 160f,
                excludedXZRadius = 80.0,
            ),
            shapeMutator = FloorSnapper(center),
        ),
        TemperatureComponent(temperatureCoolingRate = 300.0),
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(60.0f, 60.0f, 60.0f))
            .displacement(Vector3f(.0f, .5f, .0f)),
        attachColorSupplier = true,
    ).applyRadialVelocityFromCenter(
        Vector3f(-2f, -1.0f, -2f)
    )

    private val uprisingSmoke = ParticleComponent(
        ParticleEmitter(
            center, 3000.0,
            emitterShape = CylinderShape(
                height = 1f,
                radiusX = 80f,
                radiusZ = 80f,
            ),
            shapeMutator = FloorSnapper(center, center.toVector3f()),
        ),
        TemperatureComponent(temperatureCoolingRate = 300.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(60.0f, 60.0f, 60.0f))
            .displacement(Vector3f(.0f, .5f, .0f)),
        attachColorSupplier = true,
    ).applyRadialVelocityFromCenter(
        Vector3f(-1.5f, -1f, -1.5f)
    )

    private val foot = ParticleComponent(
        particleEmitter = ParticleEmitter(
            center, 3000.0,
            emitterShape = CylinderShape(
                height = 15f,
                radiusX = 30f,
                radiusZ = 30f,
            ),
        ),
        TemperatureComponent(temperatureCoolingRate = 280.0)
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(60.0f, 60.0f, 60.0f))
            .displacement(Vector3f(.0f, .5f, .0f)),
        attachColorSupplier = true,
    ).applyRadialVelocityFromCenter(
        Vector3f(-1.5f, -1f, -1.5f)
    )

    init {
        effectComponents = mutableListOf(
            nuclearFog,
            uprisingSmoke,
            foot
        )
    }

    override fun animate(delta: Float) {

    }

}