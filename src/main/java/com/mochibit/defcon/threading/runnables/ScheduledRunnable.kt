package com.mochibit.defcon.threading.runnables

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.lifecycle.CycledObject
import com.mochibit.defcon.threading.jobs.Schedulable
import kotlin.collections.ArrayDeque

class ScheduledRunnable : Runnable {
    private var maxMillisPerTick: Double = 20.0
    private val maxNanosPerTick: Long
        get() = (maxMillisPerTick * 1E6).toLong()

    private val workloadDeque: ArrayDeque<Schedulable> = ArrayDeque()

    var keepWorkersAt = 1 // Number of workloads to reschedule at once to avoid starvation
    fun keepWorkersAt(value: Int) = apply { keepWorkersAt = value }

    fun maxMillisPerTick(value: Double) = apply { maxMillisPerTick = value }

    fun addWorkload(workload: Schedulable) {
        for (i in 0 until keepWorkersAt)
            workloadDeque.add(workload)
    }

    override fun run() {
        val stopTime = System.nanoTime() + maxNanosPerTick
        val lastElement: Schedulable = workloadDeque.lastOrNull() ?: return
        var nextLoad: Schedulable? = null

        while (System.nanoTime() <= stopTime && workloadDeque.isNotEmpty() && nextLoad !== lastElement) {
            nextLoad = workloadDeque.removeFirstOrNull()
            nextLoad?.compute()
            if (nextLoad?.shouldBeRescheduled() == true) {
                for (i in 0 until keepWorkersAt)
                    if (workloadDeque.size < keepWorkersAt) workloadDeque.add(nextLoad)
            }
        }
    }
}
