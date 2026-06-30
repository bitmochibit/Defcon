/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.events.handlers.blocks

import me.mochibit.defcon.events.AutoRegisterHandler
import me.mochibit.defcon.extensions.getPluginBlock
import me.mochibit.defcon.extensions.getPluginItem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

@AutoRegisterHandler
class CustomBlockHandler : Listener {
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val pluginItem = event.itemInHand.getPluginItem()
        if (pluginItem == null) return

        val linkedBlock = pluginItem.linkedBlock ?: return
        val placeLoc = event.block.location
        linkedBlock.placeBlock(
            placeLoc.x,
            placeLoc.y,
            placeLoc.z,
            placeLoc.world
        )
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val pluginBlock = block.getPluginBlock() ?: return
        val handTool = event.player.inventory.itemInMainHand

        val linkedItem = pluginBlock.linkedItem
        if (linkedItem != null && block.isPreferredTool(handTool)) {
            event.isDropItems = false
            val itemStack = linkedItem.itemStack
            block.world.dropItemNaturally(block.location, itemStack)
        }

        pluginBlock.removeBlock(
            block.location.x,
            block.location.y,
            block.location.z,
            block.world
        )
    }
}