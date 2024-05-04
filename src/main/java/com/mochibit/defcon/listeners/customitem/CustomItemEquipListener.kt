package com.mochibit.defcon.listeners.customitem

import com.mochibit.defcon.extensions.isEquipable
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack

abstract class CustomItemEquipListener {
    @EventHandler
    fun equipItemInArmor(event: InventoryClickEvent) {
        // Check if the slot is the helmet slot
        if (event.slotType != InventoryType.SlotType.ARMOR) return
        val cursor = event.cursor ?: return
        if (cursor.type == Material.AIR) return
        if (!cursor.isEquipable()) return
        val player = event.whoClicked
        val oldItem = event.currentItem
        if (!onEquipSlot(cursor, player)) return
        event.isCancelled = true
        // Set the item in the helmet slot to the gas mask
        player.inventory.helmet = cursor
        // Set the cursor to the previous item
        player.setItemOnCursor(oldItem)
    }

    open fun onEquipSlot(equippedItem: ItemStack, player: HumanEntity): Boolean {
        return false;
    }

    abstract fun getArmorPos() : Int;
}