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
import kotlin.math.abs

class CondensationCloudVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect() {
    val maxAliveTick = 20 * 60
    var riseSpeed = 7.0

    var currentRadius = 30.0
    val ringWidth = 30.0

    override fun drawRate(): Double {
        return .5
    }

    private val condensationCloud = BaseComponent(
        ParticleShape(
            SphereBuilder()
                .withRadiusXZ(400.0)
                .withRadiusY(1.0),
            ExplosionDustParticle().apply {
                scale(Vector3(30.0, 30.0, 30.0))
                particleProperties.color = Color.fromRGB(255, 255, 255)
            },
            center
        )
            .xzPredicate { x,z ->
                val distSquared = x * x + z * z
                val innerRadius = currentRadius - ringWidth
                val outerRadius = currentRadius + ringWidth
                distSquared >= innerRadius * innerRadius && distSquared <= outerRadius * outerRadius
            }
    ).apply {
        transform = transform.translated(Vector3(0.0, 30.0, 0.0))
        applyRadialVelocityFromCenter(Vector3(4.0, 0.0, 4.0))
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


}