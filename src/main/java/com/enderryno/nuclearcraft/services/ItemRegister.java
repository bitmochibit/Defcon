package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.classes.PluginConfiguration;
import com.enderryno.nuclearcraft.enums.ConfigurationStorages;
import com.enderryno.nuclearcraft.classes.CustomItem;
import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import com.enderryno.nuclearcraft.exceptions.ItemNotRegisteredException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

/**
 * This class handles the registration of the custom items
 * All the registered items are stored and returned in a form of a HashMap(id, CustomItem)
 *
 * To initialize create an instance of ItemRegister and execute the method registerItems, it will automatically
 * load up correctly the custom items
 *
 */
public class ItemRegister {

    /**
     * Static member to access the registered items
     */
    private static HashMap<Integer, PluginItem> registeredItems = null;

    private JavaPlugin pluginInstance = null;

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    public ItemRegister(JavaPlugin pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    public boolean registerItems () {

        registeredItems = new HashMap<>();
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        PluginConfiguration itemConfigurator = new PluginConfiguration(this.pluginInstance, ConfigurationStorages.items);
        FileConfiguration itemConfig = itemConfigurator.getConfig();

        if (itemConfig == null) {
            return false;
        }

        if (itemConfig.getList("enabled-items") == null) {
            return false;
        }

        itemConfig.getList("enabled-items").forEach(item -> {

            PluginItem customItem = new CustomItem();

            int itemId = itemConfig.getInt(item + ".item-id");
            if (itemId == 0 || registeredItems.get(itemId) != null) return;

            String itemName = itemConfig.getString(item + ".item-name");
            String itemDescription = itemConfig.getString(item + ".item-description");
            String itemMinecraftId = itemConfig.getString(item + ".item-minecraft-id");

            int itemDataModelId = itemConfig.getInt(item + ".item-data-model-id");
            int itemCustomBlockId = itemConfig.getInt(item + ".custom-block-id");

            int itemStackSize = itemConfig.getInt(item + ".max-stack-size");

            boolean itemEquipable = itemConfig.getBoolean(item + ".is-equipable");
            boolean itemUsable = itemConfig.getBoolean(item + ".is-usable");
            boolean itemTransportable = itemConfig.getBoolean(item + ".is-transportable");

            customItem.setID(itemId);
            customItem.setName(itemName);
            customItem.setDescription(itemDescription);
            customItem.setMinecraftId(itemMinecraftId);

            customItem.setModelId(itemDataModelId);
            customItem.setCustomBlockId(itemCustomBlockId);

            customItem.setStackSize(itemStackSize);

            customItem.setEquipable(itemEquipable);
            customItem.setUsable(itemUsable);
            customItem.setTransportable(itemTransportable);

            String behaviour = itemConfig.getString(item + ".behaviour");
            if (behaviour == null) {
                behaviour = "generic";
            }

            customItem.setBehaviour(ItemBehaviour.fromString(behaviour));



            registeredItems.put(customItem.getID(), customItem);

        });



        itemConfigurator.saveConfig();
        return true;
    }



    /* Registered items getter */
    public static HashMap<Integer, PluginItem> getRegisteredItems() throws ItemNotRegisteredException {
        if (registeredItems == null) {
            throw new ItemNotRegisteredException("Item were not registered! Verify the initialization");
        }


        return registeredItems;
    }


}
