/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.classes

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.enums.ConfigurationStorage
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

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
            error("Could not create directory: $configDir")
        }
    }

    fun reloadConfig() {
        ensureDirectories()
        configuration = YamlConfiguration.loadConfiguration(configFile)

        Defcon.instance.getResource(configFile.name)?.use { stream ->
            val ymlFile = YamlConfiguration.loadConfiguration(InputStreamReader(stream))
            (configuration as YamlConfiguration).setDefaults(ymlFile)
        }
    }

    fun saveConfig() {
        try {
            configuration?.save(configFile)
        } catch (e: IOException) {
            error("Could not save config to $configFile: ${e.message}")
        }
    }

    private fun saveDefaultConfig() {
        if (configFile.exists()) return
        try {
            Defcon.instance.saveResource("${storage.storagePath}${storage.storageFileName}.yml", false)
        } catch (e: Exception) {
            error("Error while saving default config! ${e.message}")
        }
    }

    val config: FileConfiguration
        get() {
            if (configuration == null) reloadConfig()
            return configuration!!
        }

    companion object {
        private val configurations = mutableMapOf<ConfigurationStorage, PluginConfiguration>()

        fun initializeAll() {
            for (storage in ConfigurationStorage.entries) {
                configurations[storage] = PluginConfiguration(storage)
            }
        }

        fun get(storage: ConfigurationStorage): PluginConfiguration {
            return configurations[storage] ?: PluginConfiguration(storage)
        }

        fun saveAll() {
            for (config in configurations.values) {
                config.saveConfig()
            }
        }

        fun reloadAll() {
            for (config in configurations.values) {
                config.reloadConfig()
            }
        }
    }
}
