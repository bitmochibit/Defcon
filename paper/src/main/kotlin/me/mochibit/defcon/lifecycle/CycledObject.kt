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
import me.mochibit.defcon.threading.scheduling.runLater
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * An abstract class that provides lifecycle management with regular update cycles.
 * @param asyncHandling Whether to run on an asynchronous dispatcher or main thread
 * @param maxAliveDuration Optional maximum lifetime for this object before automatic destruction
 * @param tickRate How frequently updates should occur in milliseconds (default: 50ms)
 */
abstract class CycledObject(
    private val asyncHandling: Boolean = true,
    private val maxAliveDuration: Duration? = 10.seconds,
    private val tickRate: Long = 50
) : Lifecycled {
    // Thread-safe flag for tracking destruction state
    private val isDestroyed = AtomicBoolean(false)

    // Time tracking for delta calculations
    private var lastTickTime = System.currentTimeMillis()

    // Lazy initialization of the dispatcher to avoid unnecessary allocation
    private val dispatcher by lazy {
        if (asyncHandling) {
            Dispatchers.Default
        } else {
            Defcon.minecraftDispatcher
        }
    }

    // Job reference to control the coroutine lifecycle
    private var tickJob: Job? = null
    private var lifetimeJob: Job? = null

    /**
     * Instantiates the cycled object and starts its lifecycle.
     */
    fun instantiate() {
        if (isDestroyed.get()) {
            return
        }

        Defcon.launch(dispatcher) {
            try {
                initialize()

                tickJob = launch(dispatcher) {
                    try {
                        while (isActive && !isDestroyed.get()) {
                            val currentTime = System.currentTimeMillis()
                            val deltaTime = (currentTime - lastTickTime) / 1000.0f

                            update(deltaTime)

                            lastTickTime = currentTime
                            delay(tickRate)
                        }
                    } catch (e: CancellationException) {
                        // Expected when job is cancelled
                    } catch (e: Exception) {
                        Defcon.logger.severe("Error in update cycle: ${e.message}")
                        e.printStackTrace()
                    }
                }

                // Set up max lifetime if specified
                maxAliveDuration?.let { duration ->
                    lifetimeJob = runLater(duration, dispatcher) {
                        destroy()
                    }
                }
            } catch (e: Exception) {
                Defcon.logger.severe("Failed to initialize cycled object: ${e.message}")
                e.printStackTrace()
                destroy()
            }
        }
    }

    /**
     * Initializes the object on the appropriate dispatcher.
     */
    private suspend fun initialize() {
        coroutineScope {
            start()
            lastTickTime = System.currentTimeMillis()
        }
    }

    /**
     * Cancels all coroutines and cleans up resources.
     * Safe to call multiple times.
     */
    fun destroy() {
        // Only proceed with destruction once
        if (!isDestroyed.compareAndSet(false, true)) {
            return
        }

        Defcon.launch(dispatcher) {
            try {
                // Cancel the lifetime job if it exists
                lifetimeJob?.cancelAndJoin()

                // Cancel the tick job with a timeout
                tickJob?.let { job ->
                    job.cancel()
                    withTimeoutOrNull(30.seconds) {
                        job.join() // Wait for cancellation to complete
                    }
                }

                // Clean up resources
                stop()
            } catch (e: Exception) {
                Defcon.logger.severe("Error during object destruction: ${e.message}")
                e.printStackTrace()
            } finally {
                tickJob = null
                lifetimeJob = null
            }
        }
    }
}