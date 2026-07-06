package me.mochibit.defcon.foundation.eventbus

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.mochibit.defcon.foundation.async.EventBusScope
import me.mochibit.defcon.foundation.async.ModDispatchers
import me.mochibit.defcon.foundation.err
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

private val HIGH_FREQUENCY_EVENTS =
    setOf(
        TickEvents.ClientTickEvent::class,
    )

object EventBus {
    @PublishedApi
    internal val eventFlows = ConcurrentHashMap<Pair<KClass<*>, EventPriority>, MutableSharedFlow<ModEvent>>()

    @PublishedApi
    internal val syncListeners = ConcurrentHashMap<KClass<*>, MutableList<(ModEvent) -> Unit>>()

    inline fun <reified T : ModEvent> onSync(noinline handler: (T) -> Unit) {
        val list =
            syncListeners.getOrPut(T::class) {
                java.util.concurrent.CopyOnWriteArrayList()
            }
        @Suppress("UNCHECKED_CAST")
        list.add(handler as (ModEvent) -> Unit)
    }

    fun flowFor(
        klass: KClass<*>,
        priority: EventPriority,
    ): MutableSharedFlow<ModEvent> =
        eventFlows.getOrPut(klass to priority) {
            if (klass in HIGH_FREQUENCY_EVENTS) {
                MutableSharedFlow(
                    extraBufferCapacity = 4,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            } else {
                MutableSharedFlow(
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }
        }

    /**
     * Post [event] fire-and-forget across all priority tiers.
     */
    fun post(event: ModEvent) {
        syncListeners[event::class]?.forEach { listener ->
            try {
                listener(event)
            } catch (e: Exception) {
                "EventBus sync handler error for ${event::class.simpleName}: $e".err()
                e.printStackTrace()
            }
        }

        EventPriority.entries.forEach { priority ->
            val emitted = flowFor(event::class, priority).tryEmit(event)
            if (!emitted && event::class !in HIGH_FREQUENCY_EVENTS) {
                "[EVENTBUS] DROP ${event::class.simpleName} on priority $priority".err()
            }
        }
    }

    /**
     * Post [event] and suspend until each priority tier has processed it in order.
     */
    suspend fun postAndAwait(event: ModEvent) {
        EventPriority.entries.forEach { priority ->
            flowFor(event::class, priority).emit(event)
        }
    }

    /**
     * Subscribe to events of type [T].
     *
     * @param priority         Controls when this handler runs relative to others.
     * @param listenSubclasses If `true`, also matches subtype instances of [T].
     * @param ignoreCancelled  If `true` (default), skips cancelled events.
     * @return A [Job] — cancel it to unsubscribe.
     */
    inline fun <reified T : ModEvent> on(
        priority: EventPriority = EventPriority.NORMAL,
        listenSubclasses: Boolean = false,
        ignoreCancelled: Boolean = true,
        noinline handler: suspend (T) -> Unit,
    ): Job = on(T::class, priority, listenSubclasses, ignoreCancelled, handler)

    inline fun <reified T : ModEvent> onMcMain(
        priority: EventPriority = EventPriority.NORMAL,
        listenSubclasses: Boolean = false,
        ignoreCancelled: Boolean = true,
        noinline handler: suspend (T) -> Unit,
    ): Job =
        on<T>(priority, listenSubclasses, ignoreCancelled) { event ->
            val dispatcher =
                when ((event as? ProxyEvent)?.side) {
                    LogicalSide.SERVER -> ModDispatchers.Server()
                    else -> ModDispatchers.Client()
                }
            withContext(dispatcher) {
                handler(event)
            }
        }

    @PublishedApi
    internal fun <T : ModEvent> on(
        klass: KClass<T>,
        priority: EventPriority,
        listenSubclasses: Boolean,
        ignoreCancelled: Boolean,
        handler: suspend (T) -> Unit,
    ): Job {
        val shouldRun = { event: ModEvent ->
            val typeMatch =
                if (listenSubclasses) klass.isInstance(event) else event::class == klass
            val cancellationOk =
                priority == EventPriority.MONITOR ||
                        !ignoreCancelled ||
                        (event !is Cancellable || !event.isCancelled)
            typeMatch && cancellationOk
        }

        return flowFor(klass, priority)
            .filter { shouldRun(it) }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as T
            }.onEach { event ->
                try {
                    handler(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "EventBus handler error for ${klass.simpleName}: $e".err()
                    e.printStackTrace()
                }
            }.launchIn(EventBusScope)
    }

    /**
     * Suspend until the next event of type [T] is posted, then return it.
     */
    suspend inline fun <reified T : ModEvent> awaitFirst(
        listenSubclasses: Boolean = false,
        priority: EventPriority = EventPriority.MONITOR,
    ): T =
        flowFor(T::class, priority)
            .filter { if (listenSubclasses) it is T else it::class == T::class }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as T
            }.first()
}