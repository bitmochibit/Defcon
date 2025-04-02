/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.listeners.player

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.save.savedata.PlayerDataSave
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
            val currentMaxHealth = player.getAttribute(MAX_HEALTH)?.baseValue
            if (currentMaxHealth != null) {
                // Give back the maximum health the player had before radiation
                player.getAttribute(MAX_HEALTH)?.baseValue = currentMaxHealth + radLevel.coerceAtMost(20.0)
            }
        })
    }
}