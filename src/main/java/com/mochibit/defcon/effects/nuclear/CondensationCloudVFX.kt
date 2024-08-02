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
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.ExplosionDustParticle
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import com.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Color
import org.bukkit.Location


class CondensationCloudVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect( 20 * 60) {
    var riseSpeed = 4.0

    var currentRadius = 100.0
    val ringWidth = 30.0

    private val showOnlyRadiusPredicate: (Double, Double) -> Boolean = { x, z ->
        val distSquared = x * x + z * z
        val innerRadius = currentRadius - ringWidth
        val outerRadius = currentRadius + ringWidth
        distSquared >= innerRadius * innerRadius && distSquared <= outerRadius * outerRadius
    }

    private val condensationCloud = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle().apply {
                scale(Vector3(30.0, 30.0, 30.0))
                color(Color.WHITE)
                colorDarkenFactor(0.9, 1.0)
                colorLightenFactor(0.0, 0.0)
            },
            SphereBuilder()
                .withRadiusXZ(400.0)
                .withRadiusY(1.0),
            center
        ).apply {
            xzPredicate(showOnlyRadiusPredicate)
        }
    )
        .visible(false)
        .translate(Vector3(0.0, 30.0, 0.0))
        .apply {
            applyRadialVelocityFromCenter(Vector3(2.0, 0.0, 2.0))
        }

    private val secondaryCondensationCloud = ParticleComponent(
        ParticleShape(
            ExplosionDustParticle().apply {
                scale(Vector3(30.0, 30.0, 30.0))
                color(Color.WHITE)
                colorDarkenFactor(0.9, 1.0)
                colorLightenFactor(0.0, 0.0)

            },
            SphereBuilder()
                .withRadiusXZ(400.0)
                .withRadiusY(1.0),
            center
        ).apply {
            xzPredicate(showOnlyRadiusPredicate)
        }
    )
        .visible(false)
        .translate(Vector3(0.0, 90.0, 0.0))
        .apply {
            applyRadialVelocityFromCenter(Vector3(2.0, 0.0, 2.0))
        }

    init {
        effectComponents = mutableListOf(
            condensationCloud,
            secondaryCondensationCloud
        )
    }

    override fun animate(delta: Double) {
        if (tickAlive > 20 * 20) {
            val deltaMovement = riseSpeed * delta
            condensationCloud.translate(Vector3(0.0, deltaMovement, 0.0))
            currentRadius += deltaMovement

            secondaryCondensationCloud.translate(Vector3(0.0, deltaMovement*1.5, 0.0))
        }
    }

    override fun start() {
        super.start()
        condensationCloud.setVisibilityAfterDelay(true, 20 * 20)
        secondaryCondensationCloud.setVisibilityAfterDelay(true, 20 * 35)

    }


}