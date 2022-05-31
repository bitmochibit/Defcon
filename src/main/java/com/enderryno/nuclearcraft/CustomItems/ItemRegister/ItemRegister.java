package com.enderryno.nuclearcraft.CustomItems.ItemRegister;

import com.enderryno.nuclearcraft.Configuration.Enums.ConfigurationStorages;
import com.enderryno.nuclearcraft.Configuration.PluginConfiguration.PluginConfiguration;
import com.enderryno.nuclearcraft.CustomItems.ItemClasses.AbstractItem;
import com.enderryno.nuclearcraft.CustomItems.ItemInterfaces.GenericItem;
import com.enderryno.nuclearcraft.CustomItems.ItemListeners.*;
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

        itemConfig.getList("enabled-items").forEach(item -> {

            GenericItem customItem = new AbstractItem();

            customItem.setID(itemConfig.getInt(item + ".item-id"));
            customItem.setName(itemConfig.getString(item + ".item-name"));
            customItem.setDescription(itemConfig.getString(item + ".item-description"));
            customItem.setMinecraftId(itemConfig.getString(item + ".item-minecraft-id"));

            customItem.setModelId(itemConfig.getInt(item + ".item-data-model-id"));
            customItem.setCustomBlockId(itemConfig.getInt(item + ".custom-block-id"));

            customItem.setEquipable(itemConfig.getBoolean(item + ".is-equipable"));
            customItem.setUsable(itemConfig.getBoolean(item + ".is-usable"));
            customItem.setStackSize(itemConfig.getInt(item + ".max-stack-size"));
            customItem.setTransportable(itemConfig.getBoolean(item + ".is-transportable"));

            String eventType = itemConfig.getString(item + ".event-type");
            if (eventType == null) {
                eventType = "generic";
            }

            switch (eventType) {
                case "gas-mask":
                    customItem.setEventListener(new GasMaskListener());
                    break;
                case "gas-mask-filter":
                    customItem.setEventListener(new GasMaskFilterListener());
                    break;
                case "radiation-inhibitor":
                    customItem.setEventListener(new RadiationInhibitorListener());
                    break;
                case "geiger-counter":
                    customItem.setEventListener(new GeigerCounterListener());
                    break;
                default:
                    customItem.setEventListener(new GenericListener());
                    break;
            }



            registeredItems.put(customItem.getID(), customItem);

        });



        itemConfigurator.saveConfig();
        return true;
    }



    /* Registered items getter */
    public static HashMap<Integer, GenericItem> getRegisteredItems() throws ItemNotRegisteredException {
        if (registeredItems == null) {
            throw new ItemNotRegisteredException("Item were not registered! Verify the initialization");
        }


        return registeredItems;
    }


}
