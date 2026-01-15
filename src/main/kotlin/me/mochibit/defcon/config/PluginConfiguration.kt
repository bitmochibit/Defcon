/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.config

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.Logger.err
import me.mochibit.defcon.utils.Logger.info
import java.io.File

abstract class PluginConfiguration<out T>(
    private val configName: String,
) {
    private val resourcePath = "$configName.json"
    private val dataFolderFile = File(Defcon.dataFolder, resourcePath)

    protected val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

    protected val mutex = Mutex()

    protected fun readConfigFile(): String {
        if (!dataFolderFile.exists()) {
            throw IllegalStateException("Configuration file $resourcePath does not exist")
        }
        return dataFolderFile.readText()
    }

    @Volatile
    private var _loaded = false
    val loaded: Boolean
        get() = _loaded

    @Volatile
    private var _schema: T? = null

    protected abstract suspend fun loadSchema(): T

    protected abstract suspend fun cleanupSchema()

    suspend fun getSchema(): T {
        _schema?.let { return it }

        return mutex.withLock {
            _schema?.let { return@withLock it }
            try {
                val schema = loadSchema()
                _schema = schema
                _loaded = true
                schema
            } catch (e: Exception) {
                err("Failed to load schema for $configName: ${e.message}")
                throw e
            }
        }
    }

    suspend fun cleanup() {
        mutex.withLock {
            try {
                cleanupSchema()
                _schema = null
                _loaded = false
            } catch (e: Exception) {
                err("Error during cleanup of configuration $configName: ${e.message}")
            }
        }
    }

    suspend fun reloadConfiguration() {
        mutex.withLock {
            try {
                cleanupSchema()

                val schema = loadSchema()
                _schema = schema
                _loaded = true
            } catch (e: Exception) {
                err("Failed to reload configuration $configName: ${e.message}")
                _loaded = false
                _schema = null
                throw e
            }
        }
        info("Configuration $configName reloaded successfully")
    }

    suspend fun initialize(preload: Boolean = true) =
        coroutineScope {
            try {
                saveDefaultConfig()

                if (preload) {
                    getSchema()
                } else {
                    info("Configuration $configName initialized (lazy loading enabled)")
                }
            } catch (e: Exception) {
                err("Failed to initialize configuration $configName: ${e.message}")
                throw e
            }
        }

    private fun saveDefaultConfig() {
        // Ensure the data folder exists
        if (!Defcon.dataFolder.exists()) {
            if (!Defcon.dataFolder.mkdirs()) {
                err("Failed to create data folder: ${Defcon.dataFolder.absolutePath}")
                throw IllegalStateException("Could not create plugin data folder")
            }
        }

        if (dataFolderFile.exists()) {
            info("Configuration file $configName already exists, skipping default save.")
            return
        }

        try {
            val resource = Defcon.getResource(resourcePath)
            if (resource == null) {
                info("Resource $resourcePath not found in the jar resources, assuming it's handled by the sub-configuration.")
                return
            }

            // Ensure parent directories exist
            dataFolderFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Could not create parent directories for $resourcePath")
                }
            }

            Defcon.saveResource(resourcePath, false)

            if (!dataFolderFile.exists()) {
                throw IllegalStateException("Configuration file was not created: ${dataFolderFile.absolutePath}")
            }

            info("Default configuration saved for $configName at ${dataFolderFile.absolutePath}")
        } catch (e: Exception) {
            err("Could not save default configuration for $configName: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    companion object {
        private val configurations = mutableSetOf<PluginConfiguration<*>>()

        suspend fun loadAll() =
            coroutineScope {
                info("Loading plugin configurations...")
                configurations.add(MainConfiguration)
                configurations.add(ItemsConfiguration)
                configurations.add(BlocksConfiguration)
                configurations.add(StructuresConfiguration)

                for (config in configurations) {
                    launch {
                        try {
                            config.initialize()
                        } catch (e: Exception) {
                            err("Failed to initialize configuration ${config.configName}: ${e.message}")
                        }
                    }
                }
            }

        suspend fun cleanupAll() =
            coroutineScope {
                for (config in configurations) {
                    launch {
                        try {
                            config.cleanup()
                        } catch (e: Exception) {
                            err("Failed to cleanup configuration ${config.configName}: ${e.message}")
                        }
                    }
                }
            }
    }
}
