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
import me.mochibit.defcon.particles.templates.definition.ExplosionDustParticle
import me.mochibit.defcon.vertexgeometry.particle.ParticleShape
import me.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Color
import org.bukkit.Location
import org.joml.Vector3f


class CondensationCloudVFX(private val nuclearComponent: NuclearComponent, private val position: Location) :
    AnimatedEffect(20 * 60) {
    var riseSpeed = 4.0f

    var currentRadius = 100.0f
    val ringWidth = 30.0f

    private val showOnlyRadiusPredicate: (Double, Double) -> Boolean = { x, z ->
        val distSquared = x * x + z * z
        val innerRadius = currentRadius - ringWidth
        val outerRadius = currentRadius + ringWidth
        distSquared >= innerRadius * innerRadius && distSquared <= outerRadius * outerRadius
    }

    val condensationCloudEmitter = ParticleEmitter(position, 3000.0)

    private val condensationCloud = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle().apply {
                scale(Vector3f(60.0f, 60.0f, 60.0f))
                color(Color.WHITE)
                colorDarkenFactor(0.9, 1.0)
                colorLightenFactor(0.0, 0.0)
            },
            condensationCloudEmitter,
            SphereBuilder()
                .withRadiusXZ(400.0)
                .withRadiusY(1.0),
            position
        ).apply {
            xzPredicate = showOnlyRadiusPredicate
        }
    )
        .visible(false)
        .translate(Vector3f(0.0f, 60.0f, 0.0f))
        .apply {
            applyRadialVelocityFromCenter(Vector3f(2.0f, 0.0f, 2.0f))
        }.emitRate(50)

    private val secondaryCondensationCloud = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle().apply {
                scale(Vector3f(70.0f, 70.0f, 70.0f))
                color(Color.WHITE)
                colorDarkenFactor(0.9, 1.0)
                colorLightenFactor(0.0, 0.0)
            },
            condensationCloudEmitter,
            SphereBuilder()
                .withRadiusXZ(400.0)
                .withRadiusY(1.0),
            position
        ).apply {
            xzPredicate = showOnlyRadiusPredicate
        }
    )
        .visible(false)
        .translate(Vector3f(0.0f, 140.0f, 0.0f))
        .apply {
            applyRadialVelocityFromCenter(Vector3f(2.0f, 0.0f, 2.0f))
        }.emitRate(50)



    init {
        effectComponents = mutableListOf(
            condensationCloud,
            secondaryCondensationCloud,
        )
    }

    override fun animate(delta: Float) {
        if (tickAlive > 20 * 20) {
            val deltaMovement = riseSpeed * delta
            condensationCloud.translate(Vector3f(0.0f, deltaMovement, 0.0f))
            currentRadius += deltaMovement

            secondaryCondensationCloud.translate(Vector3f(0.0f, deltaMovement * 1.5f, 0.0f))
        }
    }

    override fun start() {
        super.start()
        condensationCloud.setVisibilityAfterDelay(true, 20 * 20)
        secondaryCondensationCloud.setVisibilityAfterDelay(true, 20 * 35)

    }


}