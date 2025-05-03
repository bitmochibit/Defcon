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
import me.mochibit.defcon.registers.ResourcePackRegister
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class LoadResourcePack : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!ResourcePackRegister.isPackRegistered) {
            Defcon.Logger.warn("Resource pack is not registered, check for errors in the console")
            return
        }
        val player = event.player
        val resourcePackInfo = if (player.address.hostString == "127.0.0.1") {
            ResourcePackRegister.localPackInfo
        } else {
            ResourcePackRegister.packInfo
        }

        val miniMessage = MiniMessage.miniMessage()
        val message = miniMessage.deserialize(
            "You must install <gradient:#FF0000:#FFFB00:#FF6A00>DEFCON</gradient> resource pack to experience all the plugin's features"
        )
        val request = ResourcePackRequest.resourcePackRequest()
            .packs(resourcePackInfo)
            .prompt(message)
            .required(false)
            .build()
        player.sendResourcePacks(request)
    }
}