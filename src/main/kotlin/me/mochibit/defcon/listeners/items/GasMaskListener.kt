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

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import me.mochibit.defcon.extensions.getBehaviour
import me.mochibit.defcon.extensions.random
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import kotlin.random.Random.Default.nextFloat

class GasMaskListener : Listener {
    @EventHandler
    fun protectFromGas(event: RadiationSuffocationEvent) {
        val player = event.getPlayer()

        // Check if the player has a gas mask
        val helmet = player.inventory.helmet ?: return

        val itemBehaviour = helmet.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return

        // Cancel the event
        event.setCancelled(true)
    }

    @EventHandler
    fun onGasMaskEquip(event: EntityEquipmentChangedEvent) {
        if (event.entity !is Player) return
        val changedSlot = event.equipmentChanges[EquipmentSlot.HEAD] ?: return
        val newItem = changedSlot.newItem()
        val itemBehaviour = newItem.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return
        event.entity.let {
            val randomizedPitch = (0.6f..0.9f).random()
            it.world.playSound(it.location, Sound.ENTITY_PLAYER_BREATH, 2.0f, randomizedPitch)
        }
    }

}
