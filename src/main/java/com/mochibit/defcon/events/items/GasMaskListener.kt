package com.mochibit.defcon.events.items

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.enums.ItemDataKey
import com.mochibit.defcon.events.definitions.RadiationSuffocationEvent
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType

class GasMaskListener : Listener {
    @EventHandler
    fun equipGasMaskDrag(event: InventoryClickEvent) {
        val cursor = event.cursor ?: return
        // Check if the slot is the helmet slot
        if (cursor.type == Material.AIR) return
        if (event.slotType != InventoryType.SlotType.ARMOR) return
        if (event.rawSlot != 5) return

        val player = event.whoClicked
        val oldItem = event.currentItem

        info("Cursor: $cursor")

        // Read item meta
        val itemMeta = cursor.itemMeta ?: return
        info("Item meta: $itemMeta")

        // Check if the item is a gas mask
        val itemBehaviour = MetaManager.getItemData<String>(itemMeta, ItemDataKey.Behaviour)
        if (itemBehaviour != ItemBehaviour.GAS_MASK.name) return

        event.isCancelled = true
        val maxStackSize = MetaManager.getItemData<Int>(itemMeta, ItemDataKey.StackSize) ?: 64

        // Set the item in the helmet slot to the gas mask
        player.inventory.helmet = cursor

        // Play a sound
        player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f)

        // Set the cursor to the previous item
        player.setItemOnCursor(oldItem)
    }


    @EventHandler
    fun protectFromGas(event: RadiationSuffocationEvent) {
        val player = event.getPlayer()
        val radiationArea = event.getRadiationArea()

        // Check if the player has a gas mask
        val helmet = player.inventory.helmet ?: return
        val itemMeta = helmet.itemMeta ?: return

        val itemBehaviour = MetaManager.getItemData<String>(itemMeta, ItemDataKey.Behaviour)
        if (itemBehaviour != ItemBehaviour.GAS_MASK.name) return

        // Cancel the event
        event.setCancelled(true)
    }
}
