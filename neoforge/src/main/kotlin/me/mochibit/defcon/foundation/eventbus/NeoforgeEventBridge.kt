package me.mochibit.defcon.foundation.eventbus

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.fml.util.thread.EffectiveSide
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.GameShuttingDownEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.registries.RegisterEvent
import kotlin.reflect.KClass

object NeoforgeEventBridge : CommonEventBridge<Event>() {
    override fun <FE : Event> registerServerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    ) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, klass.java) { event: FE ->
            if (EffectiveSide.get().isServer) {
                EventBus.post(event.mapper())
            }
        }
    }

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
        on<ServerStartedEvent>().registerServer { ServerEvents.ServerStartedEvent(server) }
        on<ServerStoppedEvent>().registerServer { ServerEvents.ServerStoppedEvent(server) }
        on<EntityJoinLevelEvent>()
            .registerBoth { side -> CommonEvents.EntityJoinLevelEvent(entity, level, side) }
        on<PlayerEvent.StartTracking>()
            .registerServer { ServerEvents.PlayerStartTrackingEntity(entity as ServerPlayer, target) }
        on<PlayerEvent.StopTracking>()
            .registerServer { ServerEvents.PlayerStopTrackingEntity(entity as ServerPlayer, target) }
        on<RegisterCommandsEvent>()
            .registerBoth { side ->
                CommonEvents.RegisterCommandsEvent(
                    dispatcher,
                    commandSelection,
                    buildContext,
                    side,
                )
            }
        on<LevelEvent.Unload>()
            .registerBoth { side -> CommonEvents.LevelUnloadEvent(level, side) }
        on<GameShuttingDownEvent>()
            .registerBoth { side -> CommonEvents.GameShuttingDownEvent(side) }
    }
}