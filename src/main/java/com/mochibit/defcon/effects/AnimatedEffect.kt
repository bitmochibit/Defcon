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

import com.mochibit.defcon.lifecycle.CycledObject

abstract class AnimatedEffect(maxAliveTick: Int = 200) : CycledObject(maxAliveTick)
{
    protected var effectComponents: MutableList<EffectComponent> = mutableListOf()

    open fun draw() {
        effectComponents.forEach { it.emit() }
    }

    abstract fun animate(delta: Double)

    override fun start() {
        effectComponents.forEach { it.start() }
    }

    override fun stop() {
        effectComponents.forEach { it.stop() }
    }

    override fun update(delta: Double) {
        draw()
        animate(delta)
        effectComponents.forEach { it.update(delta) }
    }
}
