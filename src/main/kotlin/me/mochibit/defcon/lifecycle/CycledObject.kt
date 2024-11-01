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
import me.mochibit.defcon.threading.jobs.SchedulableWorkload
import me.mochibit.defcon.threading.runnables.ScheduledRunnable
import org.bukkit.Bukkit
import kotlin.concurrent.thread

abstract class CycledObject(private val maxAliveTicks: Int = 200) : Lifecycled {
    private var isDestroyed = false
    var tickAlive: Double = 0.0
    private var lastTickTime = 0L
    private val runnable = ScheduledRunnable(40000, this.javaClass.simpleName).maxMillisPerTick(2.5)

    private val tickFunction: () -> Unit = {
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastTickTime) / 1000.0f
        lastTickTime = currentTime

        update(deltaTime)

        tickAlive++
        if (maxAliveTicks > 0 && tickAlive > maxAliveTicks) {
            destroy()
        }
    }

    private val workload = SchedulableWorkload(tickFunction) { !isDestroyed }

    private fun startAsyncUpdater() {
        thread(name = "Lifecycle: ${this.javaClass.simpleName}", priority = Thread.MAX_PRIORITY) {
            while (!isDestroyed) {
                tickFunction()
                Thread.sleep(50)
            }
        }
    }

    private fun startSyncUpdater() {
        runnable.addWorkload(workload)
        runnable.start(false)
    }

    private fun initialize() {
        start()
        lastTickTime = System.currentTimeMillis()
    }

    fun instantiate(async: Boolean) {
        if (async) {
            thread(name = "Instantiation thread for ${this.javaClass.simpleName}") {
                initialize()
                startAsyncUpdater()
            }
        } else {
            Bukkit.getScheduler().callSyncMethod(Defcon.instance) {
                initialize()
                startSyncUpdater()
            }
        }
    }

    fun destroy() {
        if (!isDestroyed) {
            isDestroyed = true
            stop()
        }
    }
}