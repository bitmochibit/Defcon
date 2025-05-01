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
package me.mochibit.defcon.lifecycle

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import me.mochibit.defcon.Defcon
import org.bukkit.Bukkit
import kotlin.coroutines.CoroutineContext

abstract class CycledObject(private val maxAliveTicks: Int = 200) : Lifecycled {
    private var isDestroyed = false
    var tickAlive: Double = 0.0
    private var lastTickTime = System.currentTimeMillis()
    private var currentTickTime = System.currentTimeMillis()

    // Job reference to control the coroutine lifecycle
    private var tickJob: Job? = null

    /**
     * Instantiates the cycled object and starts its lifecycle.
     * @param async Whether to run on Minecraft async dispatcher or main thread dispatcher
     */
    fun instantiate(async: Boolean) {
        // Use MCCoroutine to launch our coroutine
        Defcon.instance.launch {
            // Initialize on the appropriate dispatcher
            initialize(async)

            val dispatcher = if (async) {
                Dispatchers.IO
            } else {
                Defcon.instance.minecraftDispatcher
            }

            tickJob = launch(dispatcher) {
                while (isActive && !isDestroyed) {
                    currentTickTime = System.currentTimeMillis()
                    val deltaTime = (currentTickTime - lastTickTime).coerceAtLeast(50) / 1000.0f
                    update(deltaTime)

                    tickAlive++
                    if (maxAliveTicks > 0 && tickAlive > maxAliveTicks) {
                        destroy()
                        break
                    }

                    lastTickTime = currentTickTime

                    // Wait 50ms (1 tick) before the next iteration
                    delay(50)
                }
            }
        }
    }

    /**
     * Initializes the object respecting the async parameter.
     * @param async Whether to run initialization on async dispatcher or main thread
     */
    private suspend fun initialize(async: Boolean) {
        val dispatcher = if (async) {
            Dispatchers.IO
        } else {
            Defcon.instance.minecraftDispatcher
        }

        withContext(dispatcher) {
            start()
            lastTickTime = System.currentTimeMillis()
        }
    }

    /**
     * Cancels the coroutine and cleans up resources.
     */
    fun destroy() {
        if (!isDestroyed) {
            isDestroyed = true
            tickJob?.cancel()

            // Launch stop callback on the appropriate thread
            Defcon.instance.launch {
                stop()
            }
        }
    }
}