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

package me.mochibit.defcon.listeners.items

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Companion.Logger.info
import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.events.customitems.GeigerDetectEvent
import me.mochibit.defcon.extensions.getBehaviour
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GeigerCounterListener : Listener {
    @EventHandler
    fun onGeigerDetect(event: GeigerDetectEvent) {
        val player = event.player as org.bukkit.entity.Player
        val location = player.location

        if (event.radiationLevel <= 0) return;
        // Check if the item on the player's hand is a Geiger Counter
        if (player.inventory.itemInMainHand.getBehaviour() != ItemBehaviour.GEIGER_COUNTER &&
            player.inventory.itemInOffHand.getBehaviour() != ItemBehaviour.GEIGER_COUNTER) return;

        info(event.radiationLevel.toString())

        // Player a ticking sound randomly, to simulate the Geiger Counter, and with a density based on the radiation level, repeating a random number of times
        val ticks = (1.. event.radiationLevel.toInt() * 6).random()
        for (i in 0 until ticks) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
                player.playSound(location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
            }, (1..20).random().toLong())
        }
    }
}
