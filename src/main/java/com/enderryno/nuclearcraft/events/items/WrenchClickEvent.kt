package com.enderryno.nuclearcraft.events.items

import com.enderryno.nuclearcraft.enums.BlockDataKey
import com.enderryno.nuclearcraft.enums.ItemBehaviour
import com.enderryno.nuclearcraft.enums.ItemDataKey
import com.enderryno.nuclearcraft.services.ItemRegister
import com.enderryno.nuclearcraft.services.StructureRegister
import com.enderryno.nuclearcraft.utils.MetaManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

class WrenchClickEvent: Listener {

    @EventHandler
    fun onBlockClickWithWrench(event: PlayerInteractEvent) {
        // Check if the block clicked is a structure
        val clickedBlock = event.clickedBlock ?: return
        if (MetaManager.getBlockData<String>(clickedBlock.location, BlockDataKey.StructureId) == null) return

        // Check if the player is holding a wrench
        val itemInHand = event.item ?: return
        val itemID = MetaManager.getItemData<String>(itemInHand.itemMeta, ItemDataKey.ItemID) ?: return

        // Check if the item is a wrench
        val wrenchItem = ItemRegister.registeredItems[itemID] ?: return
        if (wrenchItem.behaviour != ItemBehaviour.WRENCH) return

        // The item is a wrench, so we can search for the clicked structure, the returned structures will be inserted in a menu for the player to choose from
        val query = StructureRegister().searchByBlock(clickedBlock.location)
        if (query.structures.isEmpty()) return

    }
}