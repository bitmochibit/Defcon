package me.mochibit.defcon.foundation.eventbus


import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.Connection
import java.util.function.Consumer

object ClientEvents {
    data class ClientDisconnectedEvent(
        val controller: MultiPlayerGameMode?,
        val localPlayer: LocalPlayer?,
        val networkManager: Connection?,
    ) : ClientProxyEvent {
        override val side: LogicalSide = LogicalSide.CLIENT
    }

    object ScreenEvent {
        data class Init(
            val screen: Screen,
            val listenerList: List<GuiEventListener>,
            val addListener: (GuiEventListener) -> Unit,
            val removeListener: (GuiEventListener) -> Unit,
        ) : ClientProxyEvent {
            override val side: LogicalSide = LogicalSide.CLIENT
        }
    }
}