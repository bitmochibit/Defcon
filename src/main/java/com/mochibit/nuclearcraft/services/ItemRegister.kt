package com.mochibit.nuclearcraft.services

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.classes.CustomItemDefinition
import com.mochibit.nuclearcraft.classes.PluginConfiguration
import com.mochibit.nuclearcraft.enums.ConfigurationStorages
import com.mochibit.nuclearcraft.enums.ItemBehaviour
import com.mochibit.nuclearcraft.exceptions.ItemNotRegisteredException
import com.mochibit.nuclearcraft.interfaces.PluginItem
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
            val itemId = item.toString()
            if (registeredItems[itemId] != null) return@forEach

            val itemName = itemConfig.getString("$item.item-name") ?: throw ItemNotRegisteredException(itemId)
            val itemDescription = itemConfig.getString("$item.item-description")
            val itemMinecraftId = itemConfig.getString("$item.item-minecraft-id") ?: throw ItemNotRegisteredException(itemId)
            val itemDataModelId = itemConfig.getInt("$item.item-data-model-id")
            val itemCustomBlockId = itemConfig.getString("$item.custom-block-id")

            val itemStackSize = itemConfig.getInt("$item.max-stack-size")
            val itemEquipable = itemConfig.getBoolean("$item.is-equipable")
            val itemUsable = itemConfig.getBoolean("$item.is-usable")
            val itemTransportable = itemConfig.getBoolean("$item.is-transportable")
            val itemDroppable = itemConfig.getBoolean("$item.is-droppable")


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

                    isEquipable = itemEquipable,
                    isUsable = itemUsable,
                    isTransportable = itemTransportable,
                    stackSize = itemStackSize,
                    isDroppable = itemDroppable,
                    behaviour = behaviourValue
            )
            NuclearCraft.Companion.Logger.info("Registered item $itemId")
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
