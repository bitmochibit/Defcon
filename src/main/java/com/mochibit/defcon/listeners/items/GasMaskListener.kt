package com.mochibit.defcon.listeners.items

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.events.customitems.CustomItemEquipEvent
import com.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import com.mochibit.defcon.extensions.getBehaviour
import com.mochibit.defcon.listeners.customitem.CustomItemEquipListener
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
        val player = event.getPlayer()
        val item = event.getEquippedItem()

        val itemBehaviour = item.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return event.setCancelled(true)

        player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f)
    }

}
