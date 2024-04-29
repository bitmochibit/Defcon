package com.mochibit.defcon.events.items

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.enums.ItemDataKey
import com.mochibit.defcon.events.definitions.RadiationSuffocationEvent
import com.mochibit.defcon.extensions.getBehaviour
import com.mochibit.defcon.extensions.getCustomStackSize
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

        // Check if the item is a gas mask
        val itemBehaviour = cursor.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return

        event.isCancelled = true

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

        val itemBehaviour = helmet.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return

        // Cancel the event
        event.setCancelled(true)
    }
}
