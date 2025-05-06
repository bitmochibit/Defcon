package me.mochibit.defcon.classes

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.enums.ConfigurationStorage
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.logging.Level

class PluginConfiguration(private val storage: ConfigurationStorage) {
    private val configDir = File(Defcon.instance.dataFolder, storage.storagePath)
    private val configFile = File(configDir, "${storage.storageFileName}.yml")
    private var configuration: FileConfiguration? = null

    init {
        ensureDirectories()
        saveDefaultConfig()
        reloadConfig()
    }

    private fun ensureDirectories() {
        if (!configDir.exists() && !configDir.mkdirs()) {
            Defcon.instance.logger.severe("Could not create directory: $configDir")
            throw IOException("Failed to create configuration directory")
        }
    }

    /**
     * Reloads the configuration from disk.
     * Preserves user changes while setting appropriate defaults.
     * Will add back any missing keys that should be in the config.
     */
    fun reloadConfig() {
        ensureDirectories()

        // Load the existing configuration from disk first
        if (configFile.exists()) {
            try {
                configuration = YamlConfiguration.loadConfiguration(configFile)
                Defcon.instance.logger.fine("Loaded configuration from: ${configFile.absolutePath}")
            } catch (e: Exception) {
                Defcon.instance.logger.log(Level.WARNING, "Error loading config, will create new one", e)
                // If loading fails, we'll create a new config below
                configuration = YamlConfiguration()
            }
        } else {
            // No config exists yet, create new one
            configuration = YamlConfiguration()
        }

        // Load the default values from the jar (to use as defaults only, not to overwrite)
        val resourcePath = if (storage.storagePath.isEmpty()) {
            "${storage.storageFileName}.yml"
        } else {
            "${storage.storagePath}/${storage.storageFileName}.yml"
        }

        // Set defaults and add any missing keys
        Defcon.instance.getResource(resourcePath)?.use { stream ->
            val defaultConfig = YamlConfiguration.loadConfiguration(InputStreamReader(stream))

            // Add default options with comments
            Defcon.instance.logger.fine("Checking for missing configuration keys in ${storage.storageFileName}.yml")

            // Important: Set the defaults (this doesn't modify the actual config)
            configuration?.setDefaults(defaultConfig)

            // Check for and add any missing keys
            val configChanged = addMissingConfigurationValues(defaultConfig, configuration!!)

            // Save the config only if missing keys were added
            if (configChanged) {
                Defcon.instance.logger.info("Added missing configuration keys to ${storage.storageFileName}.yml")
                saveConfig()
            }
        } ?: Defcon.instance.logger.fine("No embedded default configuration found at: $resourcePath")
    }

    /**
     * Helper method to add missing keys from default config to user config
     * WITHOUT overwriting existing values. Handles nested sections properly.
     * @return true if any keys were added, false otherwise
     */
    private fun addMissingConfigurationValues(defaultConfig: FileConfiguration, userConfig: FileConfiguration): Boolean {
        var configurationChanged = false

        // Get all keys from the default config, including nested ones
        for (key in defaultConfig.getKeys(true)) {
            // Skip if the user config already has this key
            if (userConfig.isSet(key)) {
                continue
            }

            // Check if this is a section (could have nested values)
            if (defaultConfig.isConfigurationSection(key)) {
                // For sections, we just ensure the section exists but don't copy values directly
                // The nested keys will be handled in subsequent iterations
                if (!userConfig.isConfigurationSection(key)) {
                    userConfig.createSection(key)
                    configurationChanged = true
                    Defcon.instance.logger.fine("Created missing config section: $key")
                }
            } else {
                // This is a leaf value (not a section), copy it directly
                // Include comments from default if available (for proper documentation)
                val value = defaultConfig.get(key)
                val defaultComment = defaultConfig.getComments(key)

                userConfig.set(key, value)

                // Try to preserve comments from default config
                if (defaultComment.isNotEmpty()) {
                    userConfig.setComments(key, defaultComment)
                }

                configurationChanged = true
                Defcon.instance.logger.info("Added missing config key: $key")
            }
        }

        return configurationChanged
    }

    fun saveConfig() {
        try {
            configuration?.save(configFile)
        } catch (e: IOException) {
            Defcon.instance.logger.log(Level.SEVERE, "Could not save config to $configFile", e)
            throw IOException("Failed to save configuration file", e)
        }
    }

    /**
     * Saves the default configuration file if it doesn't exist.
     * This will NOT overwrite an existing config file.
     */
    private fun saveDefaultConfig() {
        if (configFile.exists()) {
            // Don't overwrite existing config files
            return
        }

        try {
            // Ensure the correct resource path with proper separators
            val resourcePath = if (storage.storagePath.isEmpty()) {
                "${storage.storageFileName}.yml"
            } else {
                "${storage.storagePath}/${storage.storageFileName}.yml"
            }

            // First check if the resource exists
            if (Defcon.instance.getResource(resourcePath) != null) {
                // If config directory doesn't exist in plugin folder, create it first
                configFile.parentFile.mkdirs()

                // Save the resource but do NOT replace if it exists
                Defcon.instance.saveResource(resourcePath, false)
                Defcon.instance.logger.info("Created default configuration at: ${configFile.absolutePath}")
            } else {
                // Create an empty config file if no default exists
                Defcon.instance.logger.warning("No default configuration found at: $resourcePath, creating empty file")
                configFile.createNewFile()
                YamlConfiguration().save(configFile)
            }
        } catch (e: Exception) {
            Defcon.instance.logger.log(Level.SEVERE, "Error while saving default config!", e)
            throw RuntimeException("Failed to initialize default configuration", e)
        }
    }

    /**
     * Gets the configuration, ensuring it's loaded.
     * This property is cached to avoid repeated disk reads.
     */
    val config: FileConfiguration
        get() {
            if (configuration == null) {
                Defcon.instance.logger.fine("Loading configuration for ${storage.storageFileName} as it was null")
                reloadConfig()
            }
            return configuration ?: throw IllegalStateException("Configuration is null after reload")
        }

    companion object {
        private val configurations = mutableMapOf<ConfigurationStorage, PluginConfiguration>()

        fun initializeAll() {
            for (storage in ConfigurationStorage.entries) {
                configurations[storage] = PluginConfiguration(storage)
            }
        }

        /**
         * Gets a configuration instance for the specified storage type.
         * Creates and caches it if it doesn't exist yet.
         */
        fun get(storage: ConfigurationStorage): PluginConfiguration {
            return configurations[storage] ?: synchronized(configurations) {
                // Double-check locking to prevent race conditions
                configurations[storage] ?: run {
                    val newConfig = PluginConfiguration(storage)
                    configurations[storage] = newConfig
                    Defcon.instance.logger.fine("Created new configuration for: ${storage.name}")
                    newConfig
                }
            }
        }

        fun saveAll() {
            for (config in configurations.values) {
                try {
                    config.saveConfig()
                } catch (e: IOException) {
                    Defcon.instance.logger.log(Level.SEVERE, "Failed to save configuration", e)
                }
            }
        }

        fun reloadAll() {
            for (config in configurations.values) {
                config.reloadConfig()
            }
        }
    }
}