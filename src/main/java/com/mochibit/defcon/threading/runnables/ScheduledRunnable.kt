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

package com.mochibit.defcon.threading.runnables

import com.mochibit.defcon.threading.jobs.Schedulable
import kotlin.collections.ArrayDeque


class ScheduledRunnable : Runnable {
    private val maxMillisPerTick = 30
    private val maxNanosPerTick = (maxMillisPerTick * 1E6).toInt()

    private val workloadDeque: ArrayDeque<Schedulable> = ArrayDeque()

    fun addWorkload(workload: Schedulable) {
        workloadDeque.add(workload)
    }

    override fun run() {
        val stopTime = System.nanoTime() + maxNanosPerTick

        val lastElement: Schedulable = workloadDeque.lastOrNull() ?: return
        var nextLoad: Schedulable? = null

        // Compute all loads until the time is run out or the queue is empty, or we did one full cycle
        // The lastElement is here, so we don't cycle through the queue several times
        while (System.nanoTime() <= stopTime && !workloadDeque.isEmpty() && nextLoad !== lastElement) {
            nextLoad = workloadDeque.removeFirst()
            nextLoad.compute()
            if (nextLoad.shouldBeRescheduled()) {
                this.addWorkload(nextLoad)
            }
        }
    }

}