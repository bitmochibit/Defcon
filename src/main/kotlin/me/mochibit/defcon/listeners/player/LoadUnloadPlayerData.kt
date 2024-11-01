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
import me.mochibit.defcon.Defcon.Companion.Logger.info
import me.mochibit.defcon.save.savedata.PlayerDataSave
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