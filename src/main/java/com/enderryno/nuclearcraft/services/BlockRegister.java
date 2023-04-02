package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.classes.PluginConfiguration;
import com.enderryno.nuclearcraft.enums.BlockBehaviour;
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
    private static HashMap<String, PluginBlock> registeredBlocks = null;

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
     */
    public void registerBlocks () {

        registeredBlocks = new HashMap<>();
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        PluginConfiguration blockConfiguration = new PluginConfiguration(this.pluginInstance, ConfigurationStorages.blocks);
        FileConfiguration blockConfig = blockConfiguration.getConfig();

        blockConfig.getList("enabled-blocks").forEach(item -> {

            PluginBlock customBlock = new CustomBlock();

            String blockId = blockConfig.getString(item + ".block-id");
            if (blockId == null || registeredBlocks.get(blockId) != null) return;

            String blockMinecraftId = blockConfig.getString(item + ".block-minecraft-id");
            int blockDataModelId = blockConfig.getInt(item + ".block-data-model-id");

            customBlock.setID(blockId);
            customBlock.setMinecraftId(blockMinecraftId);
            customBlock.setCustomModelId(blockDataModelId);

            String behaviour = blockConfig.getString(item + ".behaviour");
            if (behaviour == null) {
                behaviour = "generic";
            }
            customBlock.setBehaviour(BlockBehaviour.fromString(behaviour));
            registeredBlocks.put(customBlock.getID(), customBlock);
        });

        blockConfiguration.saveConfig();
    }



    /* Registered items getter */
    public static HashMap<String, PluginBlock> getRegisteredBlocks() throws BlockNotRegisteredException {
        if (registeredBlocks == null) {
            throw new BlockNotRegisteredException("Block not registered for some reason. Verify the initialization");
        }
        return registeredBlocks;
    }


}
