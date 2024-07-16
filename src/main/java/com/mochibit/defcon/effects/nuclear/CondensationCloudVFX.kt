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
import com.mochibit.defcon.effects.BaseComponent
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.ExplosionDustParticle
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import com.mochibit.defcon.vertexgeometry.shapes.CylinderBuilder
import com.mochibit.defcon.vertexgeometry.shapes.SphereBuilder
import org.bukkit.Color
import org.bukkit.Location

class CondensationCloudVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect() {
    val maxAliveTick = 20 * 60 * 3
    var riseSpeed = 7.0

    var currentRadius = 100.0

    override fun drawRate(): Double {
        return 2.0
    }

    private val condensationCloud = BaseComponent(
        ParticleShape(
            CylinderBuilder()
                .withRadiusX(400.0)
                .withRadiusZ(400.0)
                .withHeight(1.0),
            ExplosionDustParticle().apply {
                maxLife(300)
                scale(Vector3(30.0, 30.0, 30.0))
                particleProperties.color = Color.fromRGB(255, 255, 255)
            },
            center
        )
            .xPredicate(::visibleWhenLessThanCurrentWidthX)
            .zPredicate(::visibleWhenLessThanCurrentWidthZ)
    ).apply {
        transform = transform.translated(Vector3(0.0, 30.0, 0.0))
        applyRadialVelocityFromCenter(Vector3(2.0, 0.0, 2.0))
        emitRate(20)
    }

    override fun draw() {
        condensationCloud.emit()
    }

    override fun animate(delta: Double) {
        val deltaMovement = riseSpeed * delta
        condensationCloud.transform = condensationCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0))

        currentRadius += deltaMovement

        if (tickAlive > maxAliveTick)
            this.destroy()
    }

    override fun start() {
        condensationCloud.buildShape()
    }

    override fun stop() {}

    private fun visibleWhenLessThanCurrentWidthX(value: Double): Boolean {
        return value < currentRadius
    }

    private fun visibleWhenLessThanCurrentWidthZ(value: Double): Boolean {
        return value < currentRadius
    }

}