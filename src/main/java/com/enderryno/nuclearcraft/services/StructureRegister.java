package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.classes.CustomBlock;
import com.enderryno.nuclearcraft.classes.PluginConfiguration;
import com.enderryno.nuclearcraft.classes.StructureBlock;
import com.enderryno.nuclearcraft.enums.BlockBehaviour;
import com.enderryno.nuclearcraft.enums.ConfigurationStorages;
import com.enderryno.nuclearcraft.enums.StructureBehaviour;
import com.enderryno.nuclearcraft.exceptions.BlockNotRegisteredException;
import com.enderryno.nuclearcraft.exceptions.StructureNotRegisteredException;
import com.enderryno.nuclearcraft.interfaces.PluginBlock;
import com.enderryno.nuclearcraft.interfaces.PluginStructure;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class StructureRegister {
    private static HashMap<String, PluginStructure> registeredStructures = null;
    private JavaPlugin pluginInstance = null;

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    public StructureRegister(JavaPlugin pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public void registerStructures() {

        registeredStructures = new HashMap<>();
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        PluginConfiguration structureConfiguration = new PluginConfiguration(this.pluginInstance, ConfigurationStorages.structures);
        FileConfiguration structureConfig = structureConfiguration.getConfig();
        final HashMap<String, PluginBlock> registeredBlocks;
        try {
            registeredBlocks = BlockRegister.getRegisteredBlocks();
        } catch (BlockNotRegisteredException e) {
            throw new RuntimeException("Unable to register structures, blocks not registered");
        }

        structureConfig.getList("enabled-structures").forEach(item -> {
            // Get behaviour from Item
            String behaviour = structureConfig.getString( item + ".behaviour");
            if (behaviour == null)
                return;

            boolean requiresInterface = structureConfig.getBoolean(item + ".requires-interface");

            StructureBehaviour structureBehaviour = StructureBehaviour.valueOf(behaviour);
            PluginStructure structure = null;
            try {
                structure = structureBehaviour.getStructureClass().getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }

            if (structure == null)
                return;

            List<String> rawDefinitions = structureConfig.getStringList(item + ".block-set");

            HashMap<String, String> customBlockDefinitions = new HashMap<>();
            for (String rawDefinition : rawDefinitions) {
                String[] definition = rawDefinition.split(":");
                customBlockDefinitions.put(definition[0], definition[1]);
            }

            List<StructureBlock> structureBlocks = structure.getStructureBlocks();
            List<StructureBlock> interfaceBlocks = structure.getInterfaceBlocks();

            customBlockDefinitions.forEach((key, value) -> {
                if (registeredBlocks.containsKey(key))
                    return;
                PluginBlock block = registeredBlocks.get(value);
            });
        });
        structureConfiguration.saveConfig();
    }


    /* Registered items getter */
    public static HashMap<String, PluginStructure> getRegisteredStructures() throws StructureNotRegisteredException {
        if (registeredStructures == null) {
            throw new StructureNotRegisteredException("Block not registered for some reason. Verify the initialization");
        }
        return registeredStructures;
    }

}
