package me.mochibit.defcon.foundation.async

import kotlinx.coroutines.suspendCancellableCoroutine
import me.mochibit.defcon.foundation.services.eventService
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.PriorityQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume


object TickClock {
    private data class Waiter(val resumeAt: Long, val continuation: Continuation<Unit>) : Comparable<Waiter> {
        override fun compareTo(other: Waiter) = resumeAt.compareTo(other.resumeAt)
    }

    private val lock = Any()
    private val waiters = PriorityQueue<Waiter>()
    private var tickCount = 0L
    private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            initialized = true
            eventService.onLevelTick { level ->
                val isCanonicalSource = level.isClientSide || (level as? ServerLevel)?.dimension() == Level.OVERWORLD
                if (isCanonicalSource) onTick()
            }
        }
    }

    private fun onTick() {
        val ready = mutableListOf<Continuation<Unit>>()
        synchronized(lock) {
            tickCount++
            while (waiters.isNotEmpty() && waiters.peek().resumeAt <= tickCount) {
                ready += waiters.poll().continuation
            }
        }
        ready.forEach { it.resume(Unit) }
    }

    suspend fun awaitTicks(count: Int = 1) {
        require(count > 0) { "count must be positive" }
        ensureInitialized()
        suspendCancellableCoroutine { cont ->
            val waiter: Waiter
            synchronized(lock) {
                waiter = Waiter(tickCount + count, cont)
                waiters += waiter
            }
            cont.invokeOnCancellation {
                synchronized(lock) { waiters -= waiter }
            }
        }
    }
}

suspend fun awaitNextTick() = TickClock.awaitTicks(1)

suspend fun <T> Iterator<T>.processTickBudgeted(
    budgetNanos: Long = 3_000_000L,
    ticksBetweenBatches: Int = 1,
    perItem: (T) -> Unit,
) {
    while (hasNext()) {
        withMainContext {
            val batchStart = System.nanoTime()
            while (hasNext() && System.nanoTime() - batchStart < budgetNanos) {
                perItem(next())
            }
        }
        if (hasNext()) TickClock.awaitTicks(ticksBetweenBatches)
    }
}

suspend fun <T> Sequence<T>.processTickBudgeted(
    budgetNanos: Long = 3_000_000L,
    ticksBetweenBatches: Int = 1,
    perItem: (T) -> Unit,
) = iterator().processTickBudgeted(budgetNanos, ticksBetweenBatches, perItem)