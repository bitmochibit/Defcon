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

package com.mochibit.defcon.listeners.items

import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.enums.ItemDataKey
import com.mochibit.defcon.registers.ItemRegister
import com.mochibit.defcon.registers.StructureRegister
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