package com.mochibit.defcon.listeners.player

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.save.savedata.PlayerDataSave
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent


class LoadUnloadPlayerData : Listener {
    // When a player joins the server, load the player's data
    // When a player leaves the server, save the player's data


    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent){
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            // Get the player's UUID
            val player = event.player
            val playerUUID = player.uniqueId.toString()

            // Save the player's data
            PlayerDataSave(playerUUID).unload()
        })
    }

}