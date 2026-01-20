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

abstract class PluginConfiguration<T>(
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

    // NEW: Provide default schema instance
    protected abstract fun getDefaultSchema(): T

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
                saveOrMergeConfig()

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

    private fun saveOrMergeConfig() {
        // Ensure the data folder exists
        if (!Defcon.dataFolder.exists()) {
            if (!Defcon.dataFolder.mkdirs()) {
                err("Failed to create data folder: ${Defcon.dataFolder.absolutePath}")
                throw IllegalStateException("Could not create plugin data folder")
            }
        }

        // Ensure parent directories exist
        dataFolderFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Could not create parent directories for $resourcePath")
            }
        }

        try {
            if (!dataFolderFile.exists()) {
                // Create new config with defaults
                val defaultSchema = getDefaultSchema()
                val jsonString =
                    json.encodeToString(
                        serializer = getSerializer(),
                        value = defaultSchema,
                    )
                dataFolderFile.writeText(jsonString)
                info("Default configuration created for $configName at ${dataFolderFile.absolutePath}")
            } else {
                // Merge existing config with defaults to add missing keys
                mergeWithDefaults()
            }
        } catch (e: Exception) {
            err("Could not save/merge configuration for $configName: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun mergeWithDefaults() {
        try {
            val existingText = dataFolderFile.readText()
            val defaultSchema = getDefaultSchema()

            // Decode existing config
            val existingSchema =
                json.decodeFromString(
                    deserializer = getSerializer(),
                    string = existingText,
                )

            // Get default JSON
            val defaultJson =
                json.encodeToString(
                    serializer = getSerializer(),
                    value = defaultSchema,
                )

            // Get existing JSON
            val existingJson =
                json.encodeToString(
                    serializer = getSerializer(),
                    value = existingSchema,
                )

            // If they're different, it means new defaults were added
            if (defaultJson != existingJson) {
                // Parse as JsonElement for deep merge
                val defaultElement = json.parseToJsonElement(defaultJson)
                val existingElement = json.parseToJsonElement(existingJson)

                // Merge (existing values take precedence, but add missing keys from defaults)
                val merged = mergeJsonElements(existingElement, defaultElement)

                // Write back merged config
                val mergedString =
                    json.encodeToString(
                        kotlinx.serialization.json.JsonElement
                            .serializer(),
                        merged,
                    )
                dataFolderFile.writeText(mergedString)
                info("Configuration $configName merged with new default values")
            }
        } catch (e: Exception) {
            // If merge fails, keep existing config
            info("Configuration $configName already exists, keeping current values")
        }
    }

    private fun mergeJsonElements(
        existing: kotlinx.serialization.json.JsonElement,
        default: kotlinx.serialization.json.JsonElement,
    ): kotlinx.serialization.json.JsonElement =
        when {
            existing is kotlinx.serialization.json.JsonObject && default is kotlinx.serialization.json.JsonObject -> {
                val merged = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

                // Add all default keys
                default.forEach { (key, defaultValue) ->
                    merged[key] =
                        if (existing.containsKey(key)) {
                            // Recursively merge nested objects
                            mergeJsonElements(existing[key]!!, defaultValue)
                        } else {
                            // Add missing key from defaults
                            defaultValue
                        }
                }

                // Add any existing keys not in defaults (user additions)
                existing.forEach { (key, value) ->
                    if (!merged.containsKey(key)) {
                        merged[key] = value
                    }
                }

                kotlinx.serialization.json.JsonObject(merged)
            }

            else -> {
                existing
            } // Prefer existing value for primitives and arrays
        }

    protected abstract fun getSerializer(): kotlinx.serialization.KSerializer<T>

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
