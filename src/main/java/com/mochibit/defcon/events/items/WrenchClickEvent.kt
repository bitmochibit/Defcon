package com.mochibit.defcon.events.items

import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.enums.ItemDataKey
import com.mochibit.defcon.services.ItemRegister
import com.mochibit.defcon.services.StructureRegister
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

class WrenchClickEvent: Listener {

    @EventHandler
    fun onBlockClickWithWrench(event: PlayerInteractEvent) {
        // Check if the player is holding a wrench
        val itemInHand = event.item ?: return;
        // Check if the block clicked is a structure
        val clickedBlock = event.clickedBlock ?: return;
        val itemID = MetaManager.getItemData<String>(itemInHand.itemMeta, ItemDataKey.ItemID) ?: return;

        // Check if the item is a wrench
        val wrenchItem = ItemRegister.registeredItems[itemID] ?: return;
        if (wrenchItem.behaviour != ItemBehaviour.WRENCH) return;

        if (MetaManager.getBlockData<String>(clickedBlock.location, BlockDataKey.StructureId) == null) return;

        // The item is a wrench, so we can search for the clicked structure, the returned structures will be inserted in a menu for the player to choose from
        val query = StructureRegister().searchByBlock(clickedBlock.location);
        if (query.structures.isEmpty()) return;


    }
}