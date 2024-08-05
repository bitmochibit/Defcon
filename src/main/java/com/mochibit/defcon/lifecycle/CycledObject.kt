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

abstract class CycledObject(protected val maxAliveTick: Int = 200) : Lifecycled {
    private var destroyed = false
    private var task: BukkitTask? = null
    protected var tickAlive: Double = 0.0
    val runnable = ScheduledRunnable().maxMillisPerTick(900.0)
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

    fun instantiate(async: Boolean) {
        val instantiationRunnable = Runnable {
            this.start()
            runnable.addWorkload(workload)

            task = if (async)
                Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, runnable, 0, 1)
            else
                Bukkit.getScheduler().runTaskTimer(Defcon.instance, runnable, 0, 1)
            tickTime = System.currentTimeMillis()
        }

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, instantiationRunnable)
        } else {
            Bukkit.getScheduler().runTask(Defcon.instance, instantiationRunnable)
        }

    }

    fun destroy() {
        destroyed = true
        task?.cancel()
        this.stop()
    }


}