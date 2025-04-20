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

import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.events.customitems.CustomItemEquipEvent
import me.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import me.mochibit.defcon.extensions.getBehaviour
import me.mochibit.defcon.listeners.customitem.CustomItemEquipListener
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack

class GasMaskListener : Listener {
    @EventHandler
    fun protectFromGas(event: RadiationSuffocationEvent) {
        val player = event.getPlayer()
        val radiationArea = event.getRadiationArea()

        // Check if the player has a gas mask
        val helmet = player.inventory.helmet ?: return

        val itemBehaviour = helmet.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return

        // Cancel the event
        event.setCancelled(true)
    }

    @EventHandler
    fun onGasMaskEquip(event: CustomItemEquipEvent) {
        val player = event.player
        val item = event.equippedItem

        val itemBehaviour = item.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return event.setCancelled(true)

        player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f)
    }

}
