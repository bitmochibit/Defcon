package com.enderryno.nuclearcraft.services

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.classes.CustomBlock
import com.enderryno.nuclearcraft.classes.PluginConfiguration
import com.enderryno.nuclearcraft.enums.BlockBehaviour
import com.enderryno.nuclearcraft.enums.ConfigurationStorages
import com.enderryno.nuclearcraft.exceptions.BlockNotRegisteredException
import com.enderryno.nuclearcraft.interfaces.PluginBlock
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
            val customBlock: PluginBlock = CustomBlock()
            val blockId = blockConfig.getString("$item.block-id")
            if (blockId == null || registeredBlocks!![blockId] != null) return@forEach
            val blockMinecraftId = blockConfig.getString("$item.block-minecraft-id")!!
            val blockDataModelId = blockConfig.getInt("$item.block-data-model-id")
            customBlock.setID(blockId)
            customBlock.setMinecraftId(blockMinecraftId)
            customBlock.setCustomModelId(blockDataModelId)
            var behaviour = blockConfig.getString("$item.behaviour")
            if (behaviour == null) {
                behaviour = "generic"
            }
            customBlock.setBehaviour(BlockBehaviour.Companion.fromString(behaviour))
            registeredBlocks!![customBlock.id] = customBlock
        }
        blockConfiguration.saveConfig()
    }

    companion object {
        /**
         * Static member to access the registered items
         */
        private var registeredBlocks: HashMap<String?, PluginBlock?>? = null

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
