package com.mochibit.defcon.threading.runnables

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.lifecycle.CycledObject
import com.mochibit.defcon.threading.jobs.Schedulable
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import kotlin.collections.ArrayDeque

class ScheduledRunnable(val maxTimeBeforeStop: Int = 5000) : Runnable {
    private var maxMillisPerTick: Double = 30.0
    fun maxMillisPerTick(value: Double) = apply { maxMillisPerTick = value }
    private val maxNanosPerTick: Long
        get() = (maxMillisPerTick * 1E6).toLong()

    private val workloadDeque: ArrayDeque<Schedulable> = ArrayDeque()
    val hasWork get() = workloadDeque.isNotEmpty()
    // Get the current time
    var workloadAddTime = System.currentTimeMillis()
    fun addWorkload(workload: Schedulable) {
        workloadDeque.add(workload)
        workloadAddTime = System.currentTimeMillis()
    }

    private val delay = 0L
    private val period = 1L
    private lateinit var updater: BukkitTask

    fun start(async: Boolean = false) : ScheduledRunnable = apply {
        updater = if (async) Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, this, delay, period)
        else Bukkit.getScheduler().runTaskTimer(Defcon.instance, this, delay, period)
    }

    override fun run() {
        val stopTime = System.nanoTime() + maxNanosPerTick
        val lastElement: Schedulable = workloadDeque.lastOrNull() ?: return
        var nextLoad: Schedulable? = null

        while (System.nanoTime() <= stopTime && workloadDeque.isNotEmpty() && nextLoad !== lastElement) {
            nextLoad = workloadDeque.removeFirstOrNull()
            nextLoad?.compute()
            if (nextLoad?.shouldBeRescheduled() == true) workloadDeque.add(nextLoad)
            Thread.yield() // Allow other threads to run
        }

        if (workloadDeque.isEmpty() && System.currentTimeMillis() - workloadAddTime > maxTimeBeforeStop) {
            updater.cancel()
        }
    }

}
