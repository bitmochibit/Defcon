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

package me.mochibit.defcon.save

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.save.savedata.SaveDataInfo
import me.mochibit.defcon.save.schemas.SaveSchema
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class AbstractSaveData<T : SaveSchema>(
    protected var schema: T,
    private val useCache: Boolean = true
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val readWriteLock = ReentrantReadWriteLock()
    private val schemaCache = ConcurrentHashMap<Int, T>()

    // Data folder determined at runtime from plugin instance
    private val dataFolder: Lazy<File> = lazy {
        val pluginFolder = Defcon.instance.dataFolder
        File(pluginFolder, "data").also { it.mkdirs() }
    }

    // Prefix derived from annotation or class name
    private val filePrefix: Lazy<String> = lazy {
        val info = this.javaClass.getAnnotation(SaveDataInfo::class.java)
        info?.name ?: this.javaClass.simpleName.lowercase()
    }

    // Maximum items per file, from annotation or default
    private val maxItemsPerFile: Lazy<Int> = lazy {
        val info = this.javaClass.getAnnotation(SaveDataInfo::class.java)
        info?.maxPerFile ?: 50
    }

    // Current page being worked with
    protected var currentPage: Int? = null

    // Optional suffix for the file name
    private var suffixSupplier: (() -> String)? = null

    /**
     * Sets a supplier for the file suffix, useful for per-world data
     */
    protected fun setSuffixSupplier(supplier: (() -> String)) {
        suffixSupplier = supplier
    }

    /**
     * Checks if a page exists on disk
     */
    protected fun pageExists(page: Int): Boolean {
        return getFile(page).exists()
    }

    /**
     * Gets the file for a specific page
     */
    private fun getFile(page: Int): File {
        val suffix = suffixSupplier?.invoke() ?: ""
        return File(dataFolder.value, "${filePrefix.value}$suffix-$page.json")
    }

    /**
     * Moves to the next page
     */
    protected fun nextPage() {
        currentPage = (currentPage ?: -1) + 1
    }

    /**
     * Loads the current page from disk
     */
    protected fun load() {
        val page = currentPage ?: 0
        val file = getFile(page)

        if (file.exists()) {
            try {
                readWriteLock.read {
                    if (useCache && schemaCache.containsKey(page)) {
                        schema = schemaCache[page] as T
                    } else {
                        val loadedSchema = gson.fromJson(file.readText(), schema.javaClass) as T
                        schema = loadedSchema
                        if (useCache) {
                            schemaCache[page] = loadedSchema
                        }
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to load data from ${file.path}", e)
            }
        }
    }

    /**
     * Saves the current schema to disk
     */
    protected fun save() {
        val page = currentPage ?: 0
        saveSchema(schema, page)
    }

    /**
     * Saves a specific schema to a specific page
     */
    protected fun saveSchema(schema: T, page: Int) {
        val file = getFile(page)
        file.parentFile.mkdirs()

        readWriteLock.write {
            try {
                // Write to temp file first for atomic write operation
                val tempFile = File(file.parentFile, file.name + ".tmp")
                tempFile.writeText(gson.toJson(schema))
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)

                // Update cache if enabled
                if (useCache) {
                    schemaCache[page] = schema
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to save data to ${file.path}", e)
            }
        }
    }

    /**
     * Gets a schema from a specific page without changing the current page
     */
    protected fun getSchema(page: Int): T? {
        val file = getFile(page)
        if (!file.exists()) {
            return null
        }

        return readWriteLock.read {
            if (useCache && schemaCache.containsKey(page)) {
                schemaCache[page] as T
            } else {
                try {
                    val loadedSchema = gson.fromJson(file.readText(), schema.javaClass) as T
                    if (useCache) {
                        schemaCache[page] = loadedSchema
                    }
                    loadedSchema
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Gets all pages that exist for this save data
     */
    protected fun getAllPages(): List<Int> {
        val suffix = suffixSupplier?.invoke() ?: ""
        val prefix = "${filePrefix.value}$suffix-"
        val suffixRegex = Regex("\\.json$")

        return dataFolder.value.listFiles { file ->
            file.name.startsWith(prefix) && file.name.endsWith(".json")
        }?.mapNotNull { file ->
            val pageStr = file.name.removePrefix(prefix).replace(suffixRegex, "")
            pageStr.toIntOrNull()
        }?.sorted() ?: emptyList()
    }

    /**
     * Finds a page with available space
     */
    protected fun findAvailablePage(): Int {
        var page = 0
        while (pageExists(page)) {
            val pageSchema = getSchema(page)
            if (pageSchema != null && pageSchema.getSize() < maxItemsPerFile.value) {
                return page
            }
            page++
        }
        return page
    }

    /**
     * Gets all data across all pages
     */
    fun getAllData(): List<Any> {
        val result = mutableListOf<Any>()
        getAllPages().forEach { page ->
            val pageSchema = getSchema(page)
            pageSchema?.getAllItems()?.let { result.addAll(it) }
        }
        return result
    }

    /**
     * Gets the maximum ID across all pages
     */
    fun getMaxId(): Int {
        var maxId = 0
        getAllPages().forEach { page ->
            val pageSchema = getSchema(page)
            val pageMaxId = pageSchema?.getMaxID() ?: 0
            if (pageMaxId > maxId) {
                maxId = pageMaxId
            }
        }
        return maxId
    }

    /**
     * Clears the cache for this save data
     */
    fun clearCache() {
        readWriteLock.write {
            schemaCache.clear()
        }
    }

    abstract class Builder<T : SaveSchema, S : AbstractSaveData<T>> {
        protected var useCache: Boolean = true
        protected var schema: T? = null

        fun withCache(useCache: Boolean): Builder<T, S> {
            this.useCache = useCache
            return this
        }

        fun withSchema(schema: T): Builder<T, S> {
            this.schema = schema
            return this
        }

        abstract fun build(): S
    }
}
