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

package me.mochibit.defcon.classes

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.enums.BlockBehaviour
import me.mochibit.defcon.enums.BlockDataKey
import me.mochibit.defcon.interfaces.PluginBlock
import me.mochibit.defcon.interfaces.PluginItem
import me.mochibit.defcon.utils.MetaManager
import com.jeff_media.customblockdata.CustomBlockData
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer

/**
 * This class defines a definitions block
 *
 */
class CustomBlockDefinition(
    override val id: String,
    override val customModelId: Int,
    override val minecraftId: String,
    override val behaviour: BlockBehaviour
) : PluginBlock{

    override fun placeBlock(item: PluginItem, location: Location) {
        MetaManager.setBlockData(location, BlockDataKey.CustomBlockId, id)
        MetaManager.setBlockData(location, BlockDataKey.ItemId, item.id)

        // Print in chat for debugging
        Defcon.instance!!.getLogger().info("Placed block ID: " +
                "${MetaManager.getBlockData<String>(location, BlockDataKey.CustomBlockId)} from item ID: ${MetaManager.getBlockData<String>(location, BlockDataKey.ItemId)}}")
    }

    override fun removeBlock(location: Location) {
        // Get block at location
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, Defcon.instance!!)
        val blockIdKey = NamespacedKey(Defcon.instance!!, "definitions-block-id")
        val itemIdKey = NamespacedKey(Defcon.instance!!, "item-id")
        blockData.remove(blockIdKey)
        blockData.remove(itemIdKey)
    }

}
