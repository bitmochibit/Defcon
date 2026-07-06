package me.mochibit.defcon.foundation.async

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import me.mochibit.defcon.foundation.err
import me.mochibit.defcon.foundation.eventbus.EventBus
import me.mochibit.defcon.foundation.eventbus.ModEventHandler
import me.mochibit.defcon.foundation.eventbus.ServerEvents
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import kotlin.coroutines.CoroutineContext

object ModDispatchers : ModEventHandler {
    private var currentServer: MinecraftServer? = null

    override fun setupEvents() {
        EventBus.on<ServerEvents.ServerStartedEvent> { event ->
            currentServer = event.server
        }
        EventBus.on<ServerEvents.ServerStoppedEvent> { event ->
            currentServer = null
        }
    }

    class Client : CoroutineDispatcher() {
        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            try {
                Minecraft.getInstance().execute(block)
            } catch (e: Exception) {
                "Error dispatching to Minecraft main thread".err()
            }
        }
    }

    class Server : CoroutineDispatcher() {
        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            try {
                currentServer?.execute(block)
            } catch (e: Exception) {
                "Error dispatching to Minecraft main thread".err()
            }
        }
    }
}