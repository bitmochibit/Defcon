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

package com.mochibit.defcon.effects

import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import org.bukkit.Effect
import org.bukkit.Location

/**
 * Represents an effect component that can be added to an effect.
 */
open class BaseComponent(val particleShape: ParticleShape): EffectComponent {
    var emitBurstProbability = 0.8
    var emitRate = 10

    override fun buildShape() {
        particleShape.buildAndAssign()
    }

    var particleVelocity: Vector3
        get() = particleShape.particle.velocity
        set(value) { particleShape.particle.velocity = value }

    var transform : Transform3D
        get() = particleShape.transform
        set(value) { particleShape.transform = value }


    var colorSupplier: ((location: Location) -> org.bukkit.Color)?
        get() = particleShape.particle.colorSupplier
        set(value) { particleShape.particle.colorSupplier = value }

    override fun emit() {
        particleShape.randomDraw(emitBurstProbability, emitRate)
    }
}