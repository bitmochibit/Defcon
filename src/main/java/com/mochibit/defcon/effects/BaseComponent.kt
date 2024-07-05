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

import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import org.bukkit.Effect
import org.bukkit.Location

/**
 * Represents an effect component that can be added to an effect.
 */
open class BaseComponent(val particleShape: ParticleShape): EffectComponent {
    var emitBurstProbability = 0.8
    var emitRate = 10
    override fun emit() {
        particleShape.randomDraw(emitBurstProbability, emitRate)
    }
}