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
import me.mochibit.defcon.explosions.NuclearComponent
import me.mochibit.defcon.math.Vector3
import me.mochibit.defcon.particles.ParticleEmitter
import me.mochibit.defcon.particles.emitter.RingSurfaceShape
import me.mochibit.defcon.particles.emitter.SphereSurfaceShape
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import me.mochibit.defcon.vertexgeometry.particle.ParticleShape
import me.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.joml.Vector3f


class CondensationCloudVFX(private val nuclearComponent: NuclearComponent, private val position: Location) :
    AnimatedEffect(20 * 60) {
    private var riseSpeed = 6.0f
    private var expandSpeed = 3.0f

    private var currentRingRadius = 110.0f


    val condensationCloud = ParticleComponent(
        particleEmitter = ParticleEmitter(
            position = position,
            3000.0,
            emitterShape = RingSurfaceShape(
                ringRadius = 110.0f,
                tubeRadius = 5.0f,
            )
        )
    ).addSpawnableParticle(
        ExplosionDustParticle()
            .scale(Vector3f(60.0f, 60.0f, 60.0f))
            .color(Color.WHITE)
    )
        .applyRadialVelocityFromCenter(Vector3f(6.0f, 0.0f, 6.0f))
        .apply {
            visible = false
        }
        .translate(Vector3f(0.0f, 150.0f, 0.0f))

    private val condensationCloudShape = condensationCloud.shape as RingSurfaceShape

//    private val secondaryCondensationCloud = ParticleComponent(
//        ParticleShape(
//            ExplosionDustParticle().apply {
//                scale(Vector3f(70.0f, 70.0f, 70.0f))
//                color(Color.WHITE)
//                colorDarkenFactor(0.9, 1.0)
//                colorLightenFactor(0.0, 0.0)
//            },
//            condensationCloudEmitter,
//            SphereBuilder()
//                .withRadiusXZ(400.0)
//                .withRadiusY(1.0),
//            position
//        ).apply {
//            xzPredicate = showOnlyRadiusPredicate
//        }
//    )
//        .visible(false)
//        .translate(Vector3f(0.0f, 140.0f, 0.0f))
//        .apply {
//            applyRadialVelocityFromCenter(Vector3f(2.0f, 0.0f, 2.0f))
//        }.emitRate(50)



    init {
        effectComponents = mutableListOf(
            condensationCloud,
            //secondaryCondensationCloud,
        )
    }

    override fun animate(delta: Float) {
        if (tickAlive > 20 * 20) {
            val deltaMovement = riseSpeed * delta
            val deltaExpansion = expandSpeed * delta
            condensationCloud.translate(Vector3f(0.0f, deltaMovement, 0.0f))

            currentRingRadius += deltaExpansion
            condensationCloudShape.ringRadius = currentRingRadius




            //secondaryCondensationCloud.translate(Vector3f(0.0f, deltaMovement * 1.5f, 0.0f))
        }
    }

    override fun start() {
        super.start()
        condensationCloud.setVisibilityAfterDelay(true, 20 * 10)
        //secondaryCondensationCloud.setVisibilityAfterDelay(true, 20 * 35)

    }


}