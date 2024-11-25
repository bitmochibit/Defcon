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

import me.mochibit.defcon.Defcon
import org.bukkit.Bukkit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class CycledObject(private val maxAliveTicks: Int = 200) : Lifecycled {
    private var isDestroyed = false
    var tickAlive: Double = 0.0
    private var lastTickTime = System.currentTimeMillis()
    private var currentTickTime = System.currentTimeMillis()

    private val executorService = Executors.newSingleThreadExecutor()

    private val tickFunction: () -> Unit = {
        currentTickTime = System.currentTimeMillis()

        update((currentTickTime - lastTickTime).coerceAtLeast(50)/1000.0f)

        tickAlive++
        if (maxAliveTicks > 0 && tickAlive > maxAliveTicks) {
            destroy()
        }
        lastTickTime = currentTickTime
    }

    fun instantiate(async: Boolean, useThreadPool: Boolean = false) {
        initialize()
        if (async) {
            if (useThreadPool) {
                // Use a thread from the thread pool for the loop
                executorService.submit {
                    while (!isDestroyed) {
                        tickFunction()
                        try {
                            TimeUnit.MILLISECONDS.sleep(50) // Wait for 1 tick (50ms)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@submit
                        }
                    }
                }
            } else {
                // Use Bukkit's async scheduler
                Bukkit.getScheduler().runTaskTimerAsynchronously(
                    Defcon.instance,
                    Runnable { if (!isDestroyed) tickFunction() },
                    0L,
                    1L // Execute every 1 tick (50ms)
                )
            }
        } else {
            // Run synchronously on the main thread
            Bukkit.getScheduler().runTaskTimer(
                Defcon.instance,
                Runnable { if (!isDestroyed) tickFunction() },
                0L,
                1L // Execute every 1 tick (50ms)
            )
        }
    }


    private fun initialize() {
        start()
        lastTickTime = System.currentTimeMillis()
    }

    fun destroy() {
        if (!isDestroyed) {
            isDestroyed = true
            stop()
            executorService.shutdown()
        }
    }
}