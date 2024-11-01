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

package me.mochibit.defcon.observer

import java.util.concurrent.CompletableFuture

interface Loadable<T> : Observable<(T) -> Unit, T> {
    var isLoaded: Boolean
    fun load()
    fun onLoad(job: (T) -> Unit) = apply {
        addObserver(job)
    }

    fun waitForOthers(loadables: List<Loadable<*>>) : CompletableFuture<Void> {
        if (loadables.isEmpty()) return CompletableFuture.completedFuture(null)
        val promises = loadables.map { it.loadPromise() }
        return CompletableFuture.allOf(*promises.toTypedArray())
    }
    fun loadPromise() : CompletableFuture<T> {
        val promise = CompletableFuture<T>()
        onLoad { data: T ->
            promise.complete(data)
        }
        load()
        return promise
    }

    override fun notifyObservers(data: T) {
        isLoaded = true
        observers.forEach { it.invoke(data) }
    }
}