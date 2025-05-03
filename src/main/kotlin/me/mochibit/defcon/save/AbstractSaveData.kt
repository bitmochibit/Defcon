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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }

    private val readWriteLock = ReentrantReadWriteLock()
    private val schemaCache = ConcurrentHashMap<Int, T>()

    // Data folder determined at runtime from plugin instance
    private val dataFolder: Lazy<File> = lazy {
        val pluginFolder = Defcon.instance.dataFolder
        File(pluginFolder, "data").also { it.mkdirs() }
    }

    // Prefix derived from annotation or class name
    private val filePrefix: Lazy<String> = lazy {
        javaClass.getAnnotation(SaveDataInfo::class.java)?.name
            ?: javaClass.simpleName.lowercase()
    }

    // Maximum items per file, from annotation or default
    private val maxItemsPerFile: Lazy<Int> = lazy {
        javaClass.getAnnotation(SaveDataInfo::class.java)?.maxPerFile ?: 50
    }

    // Current page being worked with
    @Volatile
    protected var currentPage: Int? = null

    // Optional suffix for the file name
    private var suffixSupplier: (() -> String) = {""}

    /**
     * Sets a supplier for the file suffix, useful for per-world data
     */
    protected fun setSuffixSupplier(supplier: (() -> String)) {
        suffixSupplier = supplier
    }

    /**
     * Checks if a page exists on disk
     */
    protected suspend fun pageExists(page: Int): Boolean = withContext(Dispatchers.IO) {
        getFile(page).exists()
    }

    /**
     * Gets the file for a specific page
     */
    private fun getFile(page: Int): File {
        val suffix = suffixSupplier.invoke()
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
    protected suspend fun load() = withContext(Dispatchers.IO) {
        val page = currentPage ?: 0
        val file = getFile(page)

        if (file.exists()) {
            try {
                readWriteLock.read {
                    if (useCache && schemaCache.containsKey(page)) {
                        schema = schemaCache[page] as T
                    } else {
                        val fileContent = file.readText()
                        val loadedSchema = gson.fromJson(fileContent, schema.javaClass) as T
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
    protected suspend fun save() = withContext(Dispatchers.IO) {
        val page = currentPage ?: 0
        saveSchema(schema, page)
    }

    /**
     * Saves a specific schema to a specific page
     */
    protected suspend fun saveSchema(schema: T, page: Int) = withContext(Dispatchers.IO) {
        val file = getFile(page)
        file.parentFile.mkdirs()

        withContext(Dispatchers.IO) {
            readWriteLock.write {
                try {
                    // Generate JSON string in memory first
                    val jsonContent = gson.toJson(schema)

                    // Write to temp file first for atomic write operation
                    val tempFile = File(file.parentFile, file.name + ".tmp")
                    tempFile.writeText(jsonContent)
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
    }

    /**
     * Gets a schema from a specific page without changing the current page
     */
    protected suspend fun getSchema(page: Int): T? = withContext(Dispatchers.IO) {
        val file = getFile(page)
        if (!file.exists()) {
            return@withContext null
        }

        return@withContext withContext(Dispatchers.IO) {
            readWriteLock.read {
                if (useCache && schemaCache.containsKey(page)) {
                    schemaCache[page] as T
                } else {
                    try {
                        val fileContent = file.readText()
                        val loadedSchema = gson.fromJson(fileContent, schema.javaClass) as T
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
    }

    /**
     * Gets all pages that exist for this save data
     */
    protected suspend fun getAllPages(): List<Int> = withContext(Dispatchers.IO) {
        val suffix = suffixSupplier.invoke()
        val prefix = "${filePrefix.value}$suffix-"
        val suffixRegex = Regex("\\.json$")

        return@withContext dataFolder.value.listFiles { file ->
            file.name.startsWith(prefix) && file.name.endsWith(".json")
        }?.mapNotNull { file ->
            val pageStr = file.name.removePrefix(prefix).replace(suffixRegex, "")
            pageStr.toIntOrNull()
        }?.sorted() ?: emptyList()
    }

    /**
     * Finds a page with available space
     */
    protected suspend fun findAvailablePage(): Int = withContext(Dispatchers.IO) {
        var page = 0
        while (pageExists(page)) {
            val pageSchema = getSchema(page)
            if (pageSchema != null && pageSchema.getSize() < maxItemsPerFile.value) {
                return@withContext page
            }
            page++
        }
        return@withContext page
    }

    /**
     * Gets all data across all pages
     */
    suspend fun getAllData(): List<Any> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Any>()
        val pages = getAllPages()

        pages.forEach { page ->
            val pageSchema = getSchema(page)
            pageSchema?.getAllItems()?.let { result.addAll(it) }
        }

        return@withContext result
    }

    /**
     * Gets the maximum ID across all pages
     */
    suspend fun getMaxId(): Int = withContext(Dispatchers.IO) {
        var maxId = 0
        val pages = getAllPages()

        pages.forEach { page ->
            val pageSchema = getSchema(page)
            val pageMaxId = pageSchema?.getMaxID() ?: 0
            if (pageMaxId > maxId) {
                maxId = pageMaxId
            }
        }

        return@withContext maxId
    }

    /**
     * Clears the cache for this save data
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
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