package com.enderryno.nuclearcraft.Configuration.PluginConfiguration;

import com.enderryno.nuclearcraft.Configuration.Enums.ConfigurationStorages;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Level;

public class PluginConfiguration {

    /* Strict constructor variables */
    private final JavaPlugin plugin;
    private final ConfigurationStorages configType;
    private final String filePath;
    private final String fileName;


    private File configurationFile = null;
    private File configurationFilePath = null;
    private FileConfiguration configuration = null;



    public PluginConfiguration(JavaPlugin plugin, String filePath, ConfigurationStorages configurationStorage) {
        this.plugin = plugin;
        this.configType = configurationStorage;

        this.filePath = filePath;
        this.fileName = configType.getStorageFileName() + ".yml";

        this.configurationFilePath = new File(plugin.getDataFolder(), filePath);
        this.configurationFile = new File(configurationFilePath, fileName);


        // Initialization of the default if it doesn't exist
        saveDefaultConfig();
    }

    public void reloadConfig() {
        if (this.configurationFilePath == null) {
            this.configurationFilePath = new File(plugin.getDataFolder(), filePath);
        }

        if (this.configurationFile == null) {
            this.configurationFile = new File(configurationFilePath, fileName);
        }

        this.configuration = YamlConfiguration.loadConfiguration(this.configurationFile);
        InputStream stream = plugin.getResource(fileName);
        if (stream != null) {
            YamlConfiguration YmlFile = YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
            this.configuration.setDefaults(YmlFile);
        }


    }


    public void saveConfig() {
        try {
            this.configuration.save(this.configurationFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save config to " + this.configurationFile, e);
        }
    }

    public void saveDefaultConfig() {
        if (!this.configurationFilePath.exists()) {
            if (!this.configurationFilePath.mkdirs()) {
                plugin.getLogger().log(Level.WARNING, "Could not create the directory!");
            }
        }

        if (!this.configurationFile.exists()) {
            plugin.saveResource(filePath + fileName, false);
        }


    }

    public FileConfiguration getConfig() {
        if (!configurationFilePath.exists()) {
            this.saveDefaultConfig();
        }

        if (configuration == null) {
            this.reloadConfig();
        }

        return this.configuration;

    }


    public static void save(PluginConfiguration config) {
        config.saveConfig();
        config.reloadConfig();
    }


}