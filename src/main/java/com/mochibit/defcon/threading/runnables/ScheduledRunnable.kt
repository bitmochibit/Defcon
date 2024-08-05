package com.mochibit.defcon.threading.runnables

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.lifecycle.CycledObject
import com.mochibit.defcon.threading.jobs.Schedulable
import kotlin.collections.ArrayDeque

class ScheduledRunnable : Runnable {
    private var maxMillisPerTick: Double = 30.0
    private val maxNanosPerTick: Long
        get() = (maxMillisPerTick * 1E6).toLong()

    val workloadDeque: ArrayDeque<Schedulable> = ArrayDeque()
    fun maxMillisPerTick(value: Double) = apply { maxMillisPerTick = value }

    fun addWorkload(workload: Schedulable) {
        workloadDeque.add(workload)
    }

    override fun run() {
        val stopTime = System.nanoTime() + maxNanosPerTick
        val lastElement: Schedulable = workloadDeque.lastOrNull() ?: return
        var nextLoad: Schedulable? = null

        while (System.nanoTime() <= stopTime && workloadDeque.isNotEmpty() && nextLoad !== lastElement) {
            nextLoad = workloadDeque.removeFirstOrNull()
            nextLoad?.compute()
            if (nextLoad?.shouldBeRescheduled() == true) workloadDeque.add(nextLoad)
        }
    }
}
