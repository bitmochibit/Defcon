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

package me.mochibit.defcon.listeners.customitem

import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.events.customitems.CustomItemEquipEvent
import me.mochibit.defcon.extensions.equipSlotNumber
import me.mochibit.defcon.extensions.getBehaviour
import me.mochibit.defcon.extensions.isEquipable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.inventory.ItemStack

open class CustomItemEquipListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun equipItemInArmorClick(event: InventoryClickEvent) {
        // Check if the slot is the helmet slot
        if (event.slotType != InventoryType.SlotType.ARMOR) return
        val cursor = event.cursor ?: return
        if (cursor.type == Material.AIR) return


        if (!isEquipable(cursor, event.rawSlot)) {
            event.isCancelled = true
            event.result = Event.Result.DENY
            return;
        }

        val player = event.whoClicked
        val oldItem = event.currentItem

        if (callCustomItemEquipEvent(cursor, player).isCancelled()) {
            event.isCancelled = true
            event.result = Event.Result.DENY
            return
        }

        event.isCancelled = true

        // Set the item in the helmet slot to the gas mask
        player.inventory.helmet = cursor
        // Set the cursor to the previous item
        player.setItemOnCursor(oldItem)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun shiftClickEquipItemInArmorClick(event: InventoryClickEvent) {
        // Check if the slot is the helmet slot
        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return

        val player = event.whoClicked

        if (event.click != ClickType.SHIFT_LEFT && event.click != ClickType.SHIFT_RIGHT) return
        if (event.action != InventoryAction.MOVE_TO_OTHER_INVENTORY) return
        if (!currentItem.isEquipable()) return
        if (event.slotType == InventoryType.SlotType.CRAFTING) return
        if (event.slotType == InventoryType.SlotType.ARMOR) return


        val equipmentSlot = rawSlotToEquipmentSlot(currentItem.equipSlotNumber())
        val oldItem = player.inventory.getItem(equipmentSlot)

        if (oldItem != null && oldItem.type != Material.AIR) {
            event.isCancelled = true
            event.result = Event.Result.DENY
            return
        }

        if (callCustomItemEquipEvent(currentItem, player).isCancelled()) {
            event.isCancelled = true
            event.result = Event.Result.DENY
            return
        }

        event.isCancelled = true

        player.inventory.setItem(event.slot, null)
        player.inventory.setItem(equipmentSlot, currentItem)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun equipItemInArmorDrag(event: InventoryDragEvent) {
        val cursor = event.oldCursor
        val player = event.whoClicked
        if (cursor.type == Material.AIR) return

        if (!isEquipable(cursor, event.rawSlots.stream().findFirst().orElse(0))) {
            event.isCancelled = true
            event.result = Event.Result.DENY
            return;
        }

        if (callCustomItemEquipEvent(cursor, player).isCancelled()) {
            event.isCancelled = true
            event.result = Event.Result.DENY
            return
        }

        val oldItem = player.inventory.helmet

        event.isCancelled = true

        // Set the item in the helmet slot to the gas mask
        player.inventory.helmet = cursor
        // Set the cursor to the previous item
        player.setItemOnCursor(oldItem)
    }


    private fun isEquipable(item: ItemStack, slot: Int): Boolean {
        if (!item.isEquipable()) return false
        if (item.equipSlotNumber() != slot) return false
        return true
    }

    private fun callCustomItemEquipEvent(item: ItemStack, player: HumanEntity): CustomItemEquipEvent {
        val customItemEquipEvent = CustomItemEquipEvent(item, player)
        Bukkit.getServer().pluginManager.callEvent(customItemEquipEvent)
        return customItemEquipEvent
    }

    private fun rawSlotToEquipmentSlot(rawSlot: Int): Int {
        return when (rawSlot) {
            5 -> 39
            6 -> 38
            7 -> 37
            8 -> 36
            else -> -1
        }
    }
}