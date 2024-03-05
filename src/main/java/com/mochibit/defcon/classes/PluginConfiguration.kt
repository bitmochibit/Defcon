package com.mochibit.defcon.classes

import com.mochibit.defcon.enums.ConfigurationStorages
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.logging.Level

class PluginConfiguration(private val plugin: JavaPlugin?, configurationStorage: ConfigurationStorages) {
    private val filePath: String = configurationStorage.storagePath
    private val fileName: String = configurationStorage.storageFileName + ".yml"
    private var configurationFile: File? = null
    private var configurationFilePath: File? = null
    private var configuration: FileConfiguration? = null

    init {
        configurationFilePath = File(plugin!!.dataFolder, filePath)
        configurationFile = File(configurationFilePath, fileName)

        // Initialization of the default if it doesn't exist
        saveDefaultConfig()
    }

    fun reloadConfig() {
        if (configurationFilePath == null) {
            configurationFilePath = File(plugin!!.dataFolder, filePath)
        }
        if (configurationFile == null) {
            configurationFile = File(configurationFilePath, fileName)
        }
        configuration = YamlConfiguration.loadConfiguration(configurationFile!!)
        val stream = plugin!!.getResource(fileName)
        if (stream != null) {
            val ymlFile = YamlConfiguration.loadConfiguration(InputStreamReader(stream))
            (configuration as YamlConfiguration).setDefaults(ymlFile)
        }
    }

    fun saveConfig() {
        try {
            configuration!!.save(configurationFile!!)
        } catch (e: IOException) {
            plugin!!.getLogger().log(Level.SEVERE, "Could not save config to $configurationFile", e)
        }
    }

    private fun saveDefaultConfig() {
        if (!configurationFilePath!!.exists()) {
            if (!configurationFilePath!!.mkdirs()) {
                plugin!!.getLogger().log(Level.WARNING, "Could not create the directory!")
                return
            }
        }
        if (!configurationFile!!.exists()) {
            plugin!!.saveResource(filePath + fileName, false)
        }
    }

    val config: FileConfiguration?
        get() {
            if (!configurationFilePath!!.exists()) {
                saveDefaultConfig()
            }
            if (configuration == null) {
                reloadConfig()
            }
            return configuration
        }

    companion object {
        fun save(config: PluginConfiguration) {
            config.saveConfig()
            config.reloadConfig()
        }
    }
}