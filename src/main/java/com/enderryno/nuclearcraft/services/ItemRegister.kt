package com.enderryno.nuclearcraft.services

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.classes.CustomItem
import com.enderryno.nuclearcraft.classes.PluginConfiguration
import com.enderryno.nuclearcraft.enums.ConfigurationStorages
import com.enderryno.nuclearcraft.enums.ItemBehaviour
import com.enderryno.nuclearcraft.exceptions.ItemNotRegisteredException
import com.enderryno.nuclearcraft.interfaces.PluginItem
import org.bukkit.plugin.java.JavaPlugin

/**
 * This class handles the registration of the custom items
 * All the registered items are stored and returned in a form of a HashMap(id, CustomItem)
 *
 * To initialize create an instance of ItemRegister and execute the method registerItems, it will automatically
 * load up correctly the custom items
 *
 */
class ItemRegister() {
    private val pluginInstance: JavaPlugin = JavaPlugin.getPlugin(NuclearCraft::class.java)


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
            val customItem: PluginItem = CustomItem()
            val itemId = itemConfig.getString("$item.item-id")
            if (itemId == null || registeredItems!![itemId] != null) return@forEach
            val itemName = itemConfig.getString("$item.item-name")!!
            val itemDescription = itemConfig.getString("$item.item-description")!!
            val itemMinecraftId = itemConfig.getString("$item.item-minecraft-id")!!
            val itemDataModelId = itemConfig.getInt("$item.item-data-model-id")
            val itemCustomBlockId = itemConfig.getString("$item.custom-block-id")!!
            val itemStackSize = itemConfig.getInt("$item.max-stack-size")
            val itemEquipable = itemConfig.getBoolean("$item.is-equipable")
            val itemUsable = itemConfig.getBoolean("$item.is-usable")
            val itemTransportable = itemConfig.getBoolean("$item.is-transportable")
            customItem.setID(itemId)
            customItem.setName(itemName)
            customItem.setDescription(itemDescription)
            customItem.setMinecraftId(itemMinecraftId)
            customItem.setModelId(itemDataModelId)
            customItem.setCustomBlockId(itemCustomBlockId)
            customItem.setStackSize(itemStackSize)
            customItem.setEquipable(itemEquipable)
            customItem.setUsable(itemUsable)
            customItem.setTransportable(itemTransportable)
            var behaviour = itemConfig.getString("$item.behaviour")
            if (behaviour == null) {
                behaviour = "generic"
            }
            customItem.setBehaviour(ItemBehaviour.Companion.fromString(behaviour))
            registeredItems!![customItem.id] = customItem
        }
        itemConfigurator.saveConfig()
        return true
    }

    companion object {
        /**
         * Static member to access the registered items
         */
        private var registeredItems: HashMap<String?, PluginItem?>? = null

        /* Registered items getter */
        @Throws(ItemNotRegisteredException::class)
        fun getRegisteredItems(): HashMap<String?, PluginItem?>? {
            if (registeredItems == null) {
                throw ItemNotRegisteredException("Item were not registered! Verify the initialization")
            }
            return registeredItems
        }
    }
}
