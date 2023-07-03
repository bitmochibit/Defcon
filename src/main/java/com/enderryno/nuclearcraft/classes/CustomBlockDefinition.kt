package com.enderryno.nuclearcraft.classes

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.enums.BlockBehaviour
import com.enderryno.nuclearcraft.enums.BlockDataKey
import com.enderryno.nuclearcraft.interfaces.PluginBlock
import com.enderryno.nuclearcraft.interfaces.PluginItem
import com.enderryno.nuclearcraft.utils.MetaManager
import com.jeff_media.customblockdata.CustomBlockData
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer

/**
 * This class defines a custom block
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
        NuclearCraft.instance!!.getLogger().info("Placed block ID: " +
                "${MetaManager.getBlockData<String>(location, BlockDataKey.CustomBlockId)} from item ID: ${MetaManager.getBlockData<String>(location, BlockDataKey.ItemId)}}")
    }

    override fun removeBlock(location: Location) {
        // Get block at location
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)
        val blockIdKey = NamespacedKey(NuclearCraft.instance!!, "custom-block-id")
        val itemIdKey = NamespacedKey(NuclearCraft.instance!!, "item-id")
        blockData.remove(blockIdKey)
        blockData.remove(itemIdKey)
    }

}
