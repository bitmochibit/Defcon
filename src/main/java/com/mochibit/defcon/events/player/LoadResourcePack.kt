package com.mochibit.defcon.events.player

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.nio.file.Paths

class LoadResourcePack: Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // The URL of the resource pack is located in the folder name "defconResourcePack
        val resourcePackUrl = Paths.get("defconResourcePack").toUri().toString()
        player.setResourcePack(resourcePackUrl, )
    }
}