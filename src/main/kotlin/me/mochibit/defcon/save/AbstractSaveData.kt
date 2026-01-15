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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.save.savedata.SaveDataInfo
import me.mochibit.defcon.save.schemas.SaveSchema
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for managing paginated save data with caching and atomic writes.
 *
 * @param T The schema type that extends SaveSchema
 * @param schemaSerializer The KSerializer for the schema type
 * @param createEmptySchema Factory function to create an empty schema instance
 * @param useCache Whether to enable in-memory caching of loaded schemas
 */
abstract class AbstractSaveData<T : SaveSchema>(
    private val schemaSerializer: KSerializer<T>,
    private val createEmptySchema: () -> T,
    private val useCache: Boolean = true,
) {
    protected val json: Json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val cacheMutex = Mutex()
    private val schemaCache = ConcurrentHashMap<Int, T>()

    // Per-page mutexes for fine-grained locking
    private val pageMutexes = ConcurrentHashMap<Int, Mutex>()

    private val dataFolder: File by lazy {
        File(Defcon.dataFolder, "data").apply { mkdirs() }
    }

    private val saveDataInfo: SaveDataInfo by lazy {
        javaClass.getAnnotation(SaveDataInfo::class.java)
            ?: throw IllegalStateException("${javaClass.simpleName} must be annotated with @SaveDataInfo")
    }

    private val filePrefix: String by lazy { saveDataInfo.name }
    protected val maxItemsPerFile: Int by lazy { saveDataInfo.maxPerFile }

    @Volatile
    protected var currentPage: Int? = null

    private var suffixSupplier: () -> String = { "" }

    /**
     * Sets a supplier for the file suffix, useful for per-world data
     */
    protected fun setSuffixSupplier(supplier: () -> String) {
        suffixSupplier = supplier
    }

    /**
     * Gets the mutex for a specific page
     */
    private fun getPageMutex(page: Int): Mutex = pageMutexes.computeIfAbsent(page) { Mutex() }

    /**
     * Checks if a page exists on disk
     */
    protected suspend fun pageExists(page: Int): Boolean = withContext(Dispatchers.IO) { getFile(page).exists() }

    /**
     * Gets the file for a specific page
     */
    private fun getFile(page: Int): File {
        val suffix = suffixSupplier()
        return File(dataFolder, "$filePrefix$suffix-$page.json")
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
    protected suspend fun load(): T =
        withContext(Dispatchers.IO) {
            val page = currentPage ?: 0
            loadPage(page)
        }

    /**
     * Loads a specific page from disk
     */
    private suspend fun loadPage(page: Int): T =
        withContext(Dispatchers.IO) {
            val file = getFile(page)

            if (!file.exists()) {
                return@withContext createEmptySchema()
            }

            getPageMutex(page).withLock {
                // Check cache first
                if (useCache) {
                    schemaCache[page]?.let { return@withContext it }
                }

                try {
                    val fileContent = file.readText()
                    val loadedSchema = json.decodeFromString(schemaSerializer, fileContent)

                    if (useCache) {
                        cacheMutex.withLock {
                            schemaCache[page] = loadedSchema
                        }
                    }

                    loadedSchema
                } catch (e: Exception) {
                    throw RuntimeException("Failed to load data from ${file.path}", e)
                }
            }
        }

    /**
     * Saves the current schema to disk
     */
    protected suspend fun save(schema: T): Unit =
        withContext(Dispatchers.IO) {
            val page = currentPage ?: 0
            saveSchema(schema, page)
        }

    /**
     * Saves a specific schema to a specific page with atomic write operation
     */
    protected suspend fun saveSchema(
        schema: T,
        page: Int,
    ): Unit =
        withContext(Dispatchers.IO) {
            val file = getFile(page)
            file.parentFile.mkdirs()

            getPageMutex(page).withLock {
                try {
                    val jsonContent = json.encodeToString(schemaSerializer, schema)

                    // Atomic write: write to temp file then move
                    val tempFile = File(file.parentFile, "${file.name}.tmp")
                    tempFile.writeText(jsonContent)
                    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

                    // Update cache
                    if (useCache) {
                        cacheMutex.withLock {
                            schemaCache[page] = schema
                        }
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Failed to save data to ${file.path}", e)
                }
            }
        }

    /**
     * Gets a schema from a specific page without changing the current page
     */
    protected suspend fun getSchema(page: Int): T? =
        withContext(Dispatchers.IO) {
            val file = getFile(page)
            if (!file.exists()) return@withContext null

            try {
                loadPage(page)
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Gets all pages that exist for this save data
     */
    protected suspend fun getAllPages(): List<Int> =
        withContext(Dispatchers.IO) {
            val suffix = suffixSupplier()
            val prefix = "$filePrefix$suffix-"

            dataFolder
                .listFiles { file ->
                    file.isFile && file.name.startsWith(prefix) && file.name.endsWith(".json")
                }?.mapNotNull { file ->
                    file.nameWithoutExtension
                        .removePrefix(prefix)
                        .toIntOrNull()
                }?.sorted() ?: emptyList()
        }

    /**
     * Finds a page with available space
     */
    protected suspend fun findAvailablePage(): Int =
        withContext(Dispatchers.IO) {
            generateSequence(0) { it + 1 }
                .firstOrNull { page ->
                    if (!pageExists(page)) {
                        true
                    } else {
                        val pageSchema = getSchema(page)
                        pageSchema != null && pageSchema.getSize() < maxItemsPerFile
                    }
                } ?: 0
        }

    /**
     * Gets all data across all pages
     */
    suspend fun getAllData(): List<Any> =
        withContext(Dispatchers.IO) {
            getAllPages()
                .mapNotNull { page -> getSchema(page) }
                .flatMap { it.getAllItems() }
        }

    /**
     * Gets the maximum ID across all pages
     */
    suspend fun getMaxId(): Int =
        withContext(Dispatchers.IO) {
            getAllPages()
                .mapNotNull { page -> getSchema(page)?.getMaxID() }
                .maxOrNull() ?: 0
        }

    /**
     * Clears the cache for this save data
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            schemaCache.clear()
        }
    }

    /**
     * Clears the cache for a specific page
     */
    suspend fun clearPageCache(page: Int) {
        cacheMutex.withLock {
            schemaCache.remove(page)
        }
    }

    /**
     * Pre-loads multiple pages into cache
     */
    suspend fun preloadPages(pages: List<Int>) {
        pages.forEach { page ->
            if (pageExists(page)) {
                loadPage(page)
            }
        }
    }

    abstract class Builder<T : SaveSchema, S : AbstractSaveData<T>> {
        protected var useCache: Boolean = true

        fun withCache(useCache: Boolean) =
            apply {
                this.useCache = useCache
            }

        abstract fun build(): S
    }
}
