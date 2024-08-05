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

package com.mochibit.defcon.listeners.blocks

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.interfaces.PluginItem
import com.mochibit.defcon.registers.BlockRegister
import com.mochibit.defcon.registers.ItemRegister
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class CustomBlockPlaceEvent : Listener {
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        // Get the item in the player's hand
        val item = event.itemInHand
        val customItem: PluginItem?
        val block = event.block

        // Get persistent data container
        val container = item.itemMeta.persistentDataContainer

        // Get key and check if namespace exists
        val blockIdKey = NamespacedKey(JavaPlugin.getPlugin(Defcon::class.java), "definitions-block-id")
        val itemIdKey = NamespacedKey(JavaPlugin.getPlugin(Defcon::class.java), "item-id")
        if (!container.has(blockIdKey, PersistentDataType.STRING)) return
        if (!container.has(itemIdKey, PersistentDataType.STRING)) return

        // Get the definitions block id and set it to the block
        val customBlockId = container.get(blockIdKey, PersistentDataType.STRING)?: return
        val customItemId = container.get(itemIdKey, PersistentDataType.STRING)?: return


        customItem = ItemRegister.registeredItems[customItemId] ?: throw Exception("Item not registered")

        try {
            BlockRegister.getBlock(customBlockId)
                    ?.placeBlock(customItem, block.location)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
