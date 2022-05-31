package com.enderryno.nuclearcraft.CustomItems.ItemRegister;

import com.enderryno.nuclearcraft.Configuration.Enums.ConfigurationStorages;
import com.enderryno.nuclearcraft.Configuration.PluginConfiguration.PluginConfiguration;
import com.enderryno.nuclearcraft.CustomItems.ItemInterfaces.GenericItem;
import com.enderryno.nuclearcraft.CustomItems.ItemRegister.Exceptions.ItemNotRegisteredException;
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
    private static HashMap<Integer, GenericItem> registeredItems = null;

    private final PluginConfiguration customItemConfiguration = null;
    private JavaPlugin pluginInstance = null;

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    public ItemRegister(JavaPlugin pluginInstance) {
        this.updateConfig();
        this.pluginInstance = pluginInstance;
    }

    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    public boolean registerItems () {

        registeredItems = new HashMap<>();
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        PluginConfiguration itemConfigurator = new PluginConfiguration(this.pluginInstance, "/", ConfigurationStorages.items);
        FileConfiguration itemConfig = itemConfigurator.getConfig();





        itemConfigurator.saveConfig();
        return true;
    }


    private void updateConfig() {

    }

    /* Registered items getter */
    public static HashMap<Integer, GenericItem> getRegisteredItems() throws ItemNotRegisteredException {
        if (registeredItems == null) {
            throw new ItemNotRegisteredException("Item were not registered! Verify the initialization");
        }


        return registeredItems;
    }


}
