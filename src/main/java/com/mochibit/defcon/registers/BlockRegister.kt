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

package com.mochibit.defcon.registers

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.classes.CustomBlockDefinition
import com.mochibit.defcon.classes.PluginConfiguration
import com.mochibit.defcon.enums.BlockBehaviour
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.enums.ConfigurationStorages
import com.mochibit.defcon.exceptions.BlockNotRegisteredException
import com.mochibit.defcon.interfaces.PluginBlock
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

/**
 * This class handles the registration of the definitions blocks
 * All the registered items are stored and returned in a form of a HashMap(id, CustomBlock
 *
 */
class BlockRegister() {
    private val pluginInstance: JavaPlugin = JavaPlugin.getPlugin(Defcon::class.java)

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

            val customBlock: PluginBlock = CustomBlockDefinition(
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
            val customBlockId = MetaManager.getBlockData<String>(location, BlockDataKey.CustomBlockId) ?: return null
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
