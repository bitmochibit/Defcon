package me.mochibit.defcon.threading.runnables

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.lifecycle.CycledObject
import me.mochibit.defcon.threading.jobs.Schedulable
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import kotlin.collections.ArrayDeque

class ScheduledRunnable(val maxTimeBeforeStop: Int = 5000, val name: String = "! Unnamed !") : Runnable {
    private var maxMillisPerTick: Double = 30.0
    fun maxMillisPerTick(value: Double) = apply { maxMillisPerTick = value }
    private val maxNanosPerTick: Long
        get() = (maxMillisPerTick * 1E6).toLong()

    // Using a ConcurrentLinkedQueue for thread-safe operations
    private val workloadDeque: ArrayDeque<Schedulable> = ArrayDeque()
    val hasWork get() = synchronized(workloadDeque) { workloadDeque.isNotEmpty() }

    @Volatile
    private var workloadAddTime = System.currentTimeMillis()

    fun clear() {
        synchronized(workloadDeque) { workloadDeque.clear() }
    }

    // Synchronize access to addWorkload
    fun addWorkload(workload: Schedulable) {
        synchronized(workloadDeque) {
            workloadDeque.add(workload)
            workloadAddTime = System.currentTimeMillis()
        }
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
        val lastElement: Schedulable? = synchronized(workloadDeque) { workloadDeque.lastOrNull() }
        var nextLoad: Schedulable? = null
        while (System.nanoTime() <= stopTime && synchronized(workloadDeque) { workloadDeque.isNotEmpty() } && nextLoad !== lastElement) {
            nextLoad = synchronized(workloadDeque) { workloadDeque.removeFirstOrNull() }
            nextLoad?.compute()
            if (nextLoad?.shouldBeRescheduled() == true) addWorkload(nextLoad)
            Thread.yield() // Allow other threads to run
        }

        if (synchronized(workloadDeque) { workloadDeque.isEmpty() } && System.currentTimeMillis() - workloadAddTime > maxTimeBeforeStop) {
            updater.cancel()
        }
    }
}

