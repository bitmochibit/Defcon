package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.classes.PluginConfiguration;
import com.enderryno.nuclearcraft.enums.ConfigurationStorages;
import com.enderryno.nuclearcraft.classes.CustomBlock;
import com.enderryno.nuclearcraft.interfaces.PluginBlock;
import com.enderryno.nuclearcraft.exceptions.BlockNotRegisteredException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

/**
 * This class handles the registration of the custom blocks
 * All the registered items are stored and returned in a form of a HashMap(id, CustomBlock
 *
 */
public class BlockRegister {

    /**
     * Static member to access the registered items
     */
    private static HashMap<Integer, PluginBlock> registeredBlocks = null;

    private JavaPlugin pluginInstance = null;

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    public BlockRegister(JavaPlugin pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    public boolean registerBlocks () {

        registeredBlocks = new HashMap<>();
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        PluginConfiguration itemConfigurator = new PluginConfiguration(this.pluginInstance, ConfigurationStorages.blocks);
        FileConfiguration blockConfig = itemConfigurator.getConfig();

        blockConfig.getList("enabled-blocks").forEach(item -> {

            PluginBlock customBlock = new CustomBlock();

            int blockId = blockConfig.getInt(item + ".block-id");
            if (blockId == 0 || registeredBlocks.get(blockId) != null) return;

            String blockMinecraftId = blockConfig.getString(item + ".block-minecraft-id");
            int blockDataModelId = blockConfig.getInt(item + ".block-data-model-id");

            customBlock.setID(blockId);
            customBlock.setMinecraftId(blockMinecraftId);
            customBlock.setCustomBlockId(blockDataModelId);

            String behaviour = blockConfig.getString(item + ".behaviour");
            if (behaviour == null) {
                pluginInstance.getLogger().info("[NuclearCraft] ERROR! A block can't be registered correctly because the behaviour is not set. Check the block config. ID:" + blockId);
                return;
            }


            registeredBlocks.put(customBlock.getID(), customBlock);

        });



        itemConfigurator.saveConfig();
        return true;
    }



    /* Registered items getter */
    public static HashMap<Integer, PluginBlock> getRegisteredItems() throws BlockNotRegisteredException {
        if (registeredBlocks == null) {
            throw new BlockNotRegisteredException("Block not registered for some reason. Verify the initialization");
        }
        return registeredBlocks;
    }


}
