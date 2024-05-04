package com.mochibit.defcon.listeners.items

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.ItemBehaviour
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

class GasMaskListener : CustomItemEquipListener(), Listener {
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

    override fun onEquipSlot(equippedItem: ItemStack, player: HumanEntity): Boolean {
        // Check if the item is a gas mask
        val itemBehaviour = equippedItem.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return false

        // Play a sound
        player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f)

        return true
    }

    override fun getArmorPos() : Int {
        return 5
    }
}
