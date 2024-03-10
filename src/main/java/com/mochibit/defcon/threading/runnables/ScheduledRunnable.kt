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