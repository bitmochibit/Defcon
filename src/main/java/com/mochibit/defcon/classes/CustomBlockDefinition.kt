package com.mochibit.defcon.classes

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.enums.BlockBehaviour
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.interfaces.PluginBlock
import com.mochibit.defcon.interfaces.PluginItem
import com.mochibit.defcon.utils.MetaManager
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
        Defcon.instance!!.getLogger().info("Placed block ID: " +
                "${MetaManager.getBlockData<String>(location, BlockDataKey.CustomBlockId)} from item ID: ${MetaManager.getBlockData<String>(location, BlockDataKey.ItemId)}}")
    }

    override fun removeBlock(location: Location) {
        // Get block at location
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, Defcon.instance!!)
        val blockIdKey = NamespacedKey(Defcon.instance!!, "custom-block-id")
        val itemIdKey = NamespacedKey(Defcon.instance!!, "item-id")
        blockData.remove(blockIdKey)
        blockData.remove(itemIdKey)
    }

}
