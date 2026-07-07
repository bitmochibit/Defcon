package me.mochibit.defcon.foundation.eventbus

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.fml.util.thread.EffectiveSide
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import kotlin.reflect.KClass

object NeoforgeClientEventBridge : ClientEventBridge<Event>() {
    override fun <FE : Event> registerClientListener(
        klass: KClass<FE>,
        mapper: FE.() -> ClientProxyEvent,
    ) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
            if (EffectiveSide.get().isClient) {
                EventBus.post(event.mapper())
            }
        }
    }

    override fun setupProxyEvents() {
        on<ClientPlayerNetworkEvent.LoggingOut>()
            .registerClient { ClientEvents.ClientDisconnectedEvent(multiPlayerGameMode, player, connection) }
        on<ClientTickEvent.Pre>()
            .registerClient { TickEvents.ClientTickEvent(TickEvents.Type.CLIENT, TickEvents.Phase.START) }
        on<ClientTickEvent.Post>()
            .registerClient { TickEvents.ClientTickEvent(TickEvents.Type.CLIENT, TickEvents.Phase.END) }
        on<ScreenEvent.Init.Post>()
            .registerClient { ClientEvents.ScreenEvent.Init(screen, listenersList, ::addListener, ::removeListener) }
    }
}