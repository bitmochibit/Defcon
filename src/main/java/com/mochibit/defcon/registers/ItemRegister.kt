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
import com.mochibit.defcon.classes.CustomItemDefinition
import com.mochibit.defcon.classes.PluginConfiguration
import com.mochibit.defcon.enums.ConfigurationStorages
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.exceptions.ItemNotRegisteredException
import com.mochibit.defcon.interfaces.PluginItem
import org.bukkit.plugin.java.JavaPlugin

/**
 * This class handles the registration of the definitions items
 * All the registered items are stored and returned in a form of a HashMap(id, CustomItem)
 *
 * To initialize create an instance of ItemRegister and execute the method registerItems, it will automatically
 * load up correctly the definitions items
 *
 */
class ItemRegister() {
    private val pluginInstance: JavaPlugin = JavaPlugin.getPlugin(Defcon::class.java)


    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    fun registerItems(): Boolean {
        registeredItems = HashMap()
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        val itemConfigurator = PluginConfiguration(pluginInstance, ConfigurationStorages.Items)
        val itemConfig = itemConfigurator.config ?: return false
        if (itemConfig.getList("enabled-items") == null) {
            return false
        }
        itemConfig.getList("enabled-items")!!.forEach { item: Any? ->
            val itemId = item.toString()
            if (registeredItems[itemId] != null) return@forEach

            val itemName = itemConfig.getString("$item.item-name") ?: throw ItemNotRegisteredException(itemId)
            val itemDescription = itemConfig.getString("$item.item-description")
            val itemMinecraftId = itemConfig.getString("$item.item-minecraft-id") ?: throw ItemNotRegisteredException(itemId)
            val itemDataModelId = itemConfig.getInt("$item.item-data-model-id", 0)
            val itemCustomBlockId = itemConfig.getString("$item.definitions-block-id", null)

            val itemStackSize = itemConfig.getInt("$item.max-stack-size")
            val itemUsable = itemConfig.getBoolean("$item.is-usable")
            val itemTransportable = itemConfig.getBoolean("$item.is-transportable")
            val itemDroppable = itemConfig.getBoolean("$item.is-droppable")

            val itemEquipable = itemConfig.getBoolean("$item.is-equipable", false)
            val itemEquipSlotNumber = itemConfig.getInt("$item.equip-slot-number", 0)

            var behaviourName = itemConfig.getString("$item.behaviour")
            if (behaviourName == null) {
                behaviourName = "generic"
            }
            val behaviourValue = ItemBehaviour.fromString(behaviourName) ?: throw IllegalArgumentException("Behaviour $behaviourName is not valid")

            val customItem: PluginItem = CustomItemDefinition(
                    id = itemId,
                    name = itemName,
                    description = itemDescription,
                    minecraftId = itemMinecraftId,
                    modelId = itemDataModelId,
                    customBlockId = itemCustomBlockId,
                    equipSlotNumber = itemEquipSlotNumber,

                    isEquipable = itemEquipable,
                    isUsable = itemUsable,
                    isTransportable = itemTransportable,
                    stackSize = itemStackSize,
                    isDroppable = itemDroppable,
                    behaviour = behaviourValue
            )
            Defcon.Companion.Logger.info("Registered item $itemId")
            registeredItems[customItem.id] = customItem
        }
        itemConfigurator.saveConfig()
        return true
    }

    companion object {
        /**
         * Static member to access the registered items
         */
        var registeredItems: HashMap<String?, PluginItem?> = HashMap()

    }
}
