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

package com.mochibit.defcon.lifecycle

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.threading.jobs.SchedulableWorkload
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import kotlin.concurrent.thread

abstract class CycledObject(private val maxAliveTick: Int = 200) : Lifecycled {
    private var destroyed = false
    protected var tickAlive: Double = 0.0
    val runnable = ScheduledRunnable(40000, this.javaClass.simpleName).maxMillisPerTick(2.5)
    private var tickTime = 0L
    private val tickFun = {
        // Calculate the time passed since the last tick
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - tickTime
        // Calculate the delta time based on the time passed
        val deltaTime = timePassed / 1000.0
        this.update(deltaTime)
        tickTime = System.currentTimeMillis()
        if (maxAliveTick != 0 && tickAlive > maxAliveTick)
            this.destroy()
        tickAlive += 1
    }
    private val workload =
        SchedulableWorkload(tickFun) { !this.destroyed }

    private fun startUpdater() {
        thread(name="Lifecycle: ${this.javaClass.simpleName}", priority = Thread.MAX_PRIORITY) {
            while (!this.destroyed) {
                tickFun()
                // Wait the next tick
                Thread.sleep(50)
            }
        }
    }

    private val instantiationFunction = {
        this.start()
        startUpdater()
        tickTime = System.currentTimeMillis()
    }
    fun instantiate(async: Boolean) {
        if (async) {
            thread(name="Instantiation thread for ${this.javaClass.simpleName}") {
                instantiationFunction()
            }
        } else {
            Bukkit.getScheduler().callSyncMethod(Defcon.instance, instantiationFunction)
        }
    }

    fun destroy() {
        destroyed = true
        this.stop()
    }


}