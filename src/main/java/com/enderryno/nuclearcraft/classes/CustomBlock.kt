package com.enderryno.nuclearcraft.classes

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.enums.BlockBehaviour
import com.enderryno.nuclearcraft.interfaces.PluginBlock
import com.enderryno.nuclearcraft.interfaces.PluginItem
import com.enderryno.nuclearcraft.services.BlockRegister
import com.jeff_media.customblockdata.CustomBlockData
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/**
 * This class instantiates a custom item by its id
 *
 */
class CustomBlock : PluginBlock{

    override var id: String? = null
        private set
    override var minecraftId: String? = null
        private set
    override var behaviour: BlockBehaviour? = null
        private set
    override var customModelId = 0
    override fun setID(id: String?): PluginBlock {
        this.id = id
        return this
    }

    override fun setCustomModelId(customModelId: Int): PluginBlock {
        this.customModelId = customModelId
        return this
    }

    override fun setMinecraftId(minecraftId: String?): PluginBlock {
        this.minecraftId = minecraftId
        return this
    }

    override fun placeBlock(item: PluginItem?, location: Location) {
        // Get block at location
        val block = location.world.getBlockAt(location)
        // Save metadata
        val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)
        val blockIdKey = NamespacedKey(NuclearCraft.instance!!, "custom-block-id")
        val itemIdKey = NamespacedKey(NuclearCraft.instance!!, "item-id")
        blockData.set(blockIdKey, PersistentDataType.STRING, id!!)
        blockData.set(itemIdKey, PersistentDataType.STRING, item?.id!!)

        // Print in chat for debugging
        NuclearCraft.instance!!.getLogger().info("Placed block " + blockData.get(blockIdKey, PersistentDataType.STRING))
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

    override fun setBehaviour(behaviour: BlockBehaviour?): PluginBlock {
        this.behaviour = behaviour
        return this
    }
}
