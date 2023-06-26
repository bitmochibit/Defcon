package com.enderryno.nuclearcraft.services

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.classes.CustomBlock
import com.enderryno.nuclearcraft.classes.PluginConfiguration
import com.enderryno.nuclearcraft.enums.BlockBehaviour
import com.enderryno.nuclearcraft.enums.ConfigurationStorages
import com.enderryno.nuclearcraft.exceptions.BlockNotRegisteredException
import com.enderryno.nuclearcraft.interfaces.PluginBlock
import com.jeff_media.customblockdata.CustomBlockData
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * This class handles the registration of the custom blocks
 * All the registered items are stored and returned in a form of a HashMap(id, CustomBlock
 *
 */
class BlockRegister() {
    private val pluginInstance: JavaPlugin = JavaPlugin.getPlugin(NuclearCraft::class.java)

    /**
     *
     */
    fun registerBlocks() {
        registeredBlocks = HashMap()
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        val blockConfiguration = PluginConfiguration(pluginInstance, ConfigurationStorages.Blocks)
        val blockConfig = blockConfiguration.config
        blockConfig!!.getList("enabled-blocks")!!.forEach { item: Any? ->

            val blockId = item.toString()
            if (registeredBlocks!![blockId] != null) return@forEach
            val blockMinecraftId = blockConfig.getString("$item.block-minecraft-id") ?: throw BlockNotRegisteredException(blockId)
            val blockDataModelId = blockConfig.getInt("$item.block-data-model-id")


            var behaviourName = blockConfig.getString("$item.behaviour")
            if (behaviourName == null) {
                behaviourName = "generic"
            }
            val behaviourValue = BlockBehaviour.fromString(behaviourName) ?: throw BlockNotRegisteredException(blockId)

            val customBlock: PluginBlock = CustomBlock(
                id = blockId,
                customModelId = blockDataModelId,
                minecraftId = blockMinecraftId,
                behaviour = behaviourValue)

            registeredBlocks!![customBlock.id] = customBlock
        }
        blockConfiguration.saveConfig()
    }

    companion object {
        /**
         * Static member to access the registered items
         */
        private var registeredBlocks: HashMap<String?, PluginBlock?>? = null

        fun getBlock(location: Location): PluginBlock? {
            // Try to get block metadata
            val block = location.world.getBlockAt(location)
            val blockIdKey = NamespacedKey(NuclearCraft.instance!!, "custom-block-id")
            val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)
            val customBlockId = blockData.get(blockIdKey, PersistentDataType.STRING) ?: return null

            return getBlock(customBlockId)
        }

        fun getBlock(id: String): PluginBlock? {
            return registeredBlocks!![id]
        }

        /* Registered items getter */
        @Throws(BlockNotRegisteredException::class)
        fun getRegisteredBlocks(): HashMap<String?, PluginBlock?>? {
            if (registeredBlocks == null) {
                throw BlockNotRegisteredException("Block not registered for some reason. Verify the initialization")
            }
            return registeredBlocks
        }
    }
}