package com.mochibit.nuclearcraft.classes

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.enums.BlockBehaviour
import com.mochibit.nuclearcraft.enums.BlockDataKey
import com.mochibit.nuclearcraft.interfaces.PluginBlock
import com.mochibit.nuclearcraft.interfaces.PluginItem
import com.mochibit.nuclearcraft.utils.MetaManager
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
