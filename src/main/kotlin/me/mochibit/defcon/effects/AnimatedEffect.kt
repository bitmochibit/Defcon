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

package me.mochibit.defcon.effects

import me.mochibit.defcon.lifecycle.CycledObject
import me.mochibit.defcon.observer.Loadable

abstract class AnimatedEffect(maxAliveTick: Long = 200, protected var effectComponents: MutableList<EffectComponent> = mutableListOf()) : CycledObject(maxAliveTick), Loadable<Unit>
{
    override var isLoaded: Boolean = false
    override val observers: MutableList<(Unit) -> Unit> = mutableListOf()

    abstract fun animate(delta: Float)

    override fun start() {
        if (!isLoaded) { // This part is useful for preloading
            loadPromise().join()
        }
        effectComponents.forEach { it.start() }
    }

    override fun stop() {
        effectComponents.forEach { it.stop() }
    }

    override fun update(delta: Float) {
        if (!isLoaded) return
        animate(delta)
        effectComponents.forEach { it.update(delta) }
    }



    override fun load() {
        // Get effect components which are Loadable
        val loadableEffectComponents = effectComponents.filterIsInstance<Loadable<Unit>>()
        waitForOthers(loadableEffectComponents).thenAccept {
            isLoaded = true
            observers.forEach { it.invoke(Unit) }
        }.exceptionally{ex->
            println("Error loading effect components: ${ex.message}")
            null
        }
        // Start loading
        loadableEffectComponents.forEach { it.load() }
    }
}
