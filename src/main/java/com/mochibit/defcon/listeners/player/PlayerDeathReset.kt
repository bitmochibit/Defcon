package com.mochibit.defcon.listeners.player

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.save.savedata.PlayerDataSave
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class PlayerDeathReset : Listener {
    // When a player dies, reset the player's data
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent){
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            // Get the player's UUID
            val player = event.player
            val playerUUID = player.uniqueId.toString()

            // Reset the player's data
            val playerData = PlayerDataSave(playerUUID)
            val radLevel = playerData.getRadiationLevel()
            playerData.resetRadiationLevel()
            // Reset the player's max health
            val currentMaxHealth = player.getAttribute(GENERIC_MAX_HEALTH)?.baseValue
            if (currentMaxHealth != null) {
                // Give back the maximum health the player had before radiation
                player.getAttribute(GENERIC_MAX_HEALTH)?.baseValue = currentMaxHealth + radLevel.coerceAtMost(20.0)
            }
        })
    }
}