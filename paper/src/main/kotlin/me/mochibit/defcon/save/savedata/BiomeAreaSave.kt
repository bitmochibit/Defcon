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

package me.mochibit.defcon.save.savedata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.save.AbstractSaveData
import me.mochibit.defcon.save.schemas.BiomeAreaSaveSchema
import me.mochibit.defcon.save.schemas.toCustomBiomeBoundary
import me.mochibit.defcon.save.schemas.toSchema
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@SaveDataInfo("saved_biomes", maxPerFile = 50)
class BiomeAreaSave private constructor(
    worldName: String,
    useCache: Boolean = true,
) : AbstractSaveData<BiomeAreaSaveSchema>(
        schemaSerializer = serializer(),
        createEmptySchema = { BiomeAreaSaveSchema() },
        useCache = useCache,
    ) {
    private val maxId = AtomicInteger(0)

    init {
        setSuffixSupplier { "-$worldName" }
        // Initialize maxId asynchronously
        runBlocking {
            maxId.set(getMaxId())
        }
    }

    companion object {
        private val saves = ConcurrentHashMap<String, BiomeAreaSave>()

        fun getSave(worldName: String): BiomeAreaSave = saves.computeIfAbsent(worldName) { name -> BiomeAreaSave(name) }
    }

    /**
     * Adds a new biome area
     */
    suspend fun addBiome(biome: CustomBiomeHandler.CustomBiomeBoundary): CustomBiomeHandler.CustomBiomeBoundary =
        withContext(Dispatchers.IO) {
            val page = findAvailablePage()
            currentPage = page
            val schema = load()

            val newId = maxId.incrementAndGet()
            val indexedArea = biome.copy(id = newId)
            schema.biomeAreas.add(indexedArea.toSchema())
            save(schema)

            indexedArea
        }

    /**
     * Updates an existing biome area
     */
    suspend fun updateBiome(biome: CustomBiomeHandler.CustomBiomeBoundary): Boolean =
        withContext(Dispatchers.IO) {
            val page = findAvailablePage()
            currentPage = page
            val schema = load()

            val existingArea = schema.biomeAreas.find { it.id == biome.id }
            if (existingArea != null) {
                schema.biomeAreas.remove(existingArea)
                schema.biomeAreas.add(biome.toSchema())
                save(schema)
                true
            } else {
                false
            }
        }

    /**
     * Gets all biome boundaries across all pages
     */
    suspend fun getAll(): Set<CustomBiomeHandler.CustomBiomeBoundary> =
        withContext(Dispatchers.IO) {
            getAllPages().flatMapTo(HashSet()) { page ->
                getSchema(page)?.biomeAreas?.map { it.toCustomBiomeBoundary() } ?: emptyList()
            }
        }

    /**
     * Gets a biome area by ID along with its page number
     */
    suspend fun get(id: Int): Pair<CustomBiomeHandler.CustomBiomeBoundary, Int>? =
        withContext(Dispatchers.IO) {
            for (page in getAllPages()) {
                val schema = getSchema(page) ?: continue
                val area = schema.biomeAreas.find { it.id == id }
                if (area != null) {
                    return@withContext Pair(area.toCustomBiomeBoundary(), page)
                }
            }
            null
        }

    /**
     * Deletes a biome area by ID
     */
    suspend fun delete(id: Int): Boolean =
        withContext(Dispatchers.IO) {
            val (area, page) = get(id) ?: return@withContext false
            val schema = getSchema(page) ?: return@withContext false

            val removed = schema.biomeAreas.remove(area.toSchema())
            if (removed) {
                saveSchema(schema, page)
            }
            removed
        }

    /**
     * Builder for BiomeAreaSave
     */
    class Builder : AbstractSaveData.Builder<BiomeAreaSaveSchema, BiomeAreaSave>() {
        private var worldName: String = "world"

        fun forWorld(worldName: String) =
            apply {
                this.worldName = worldName
            }

        override fun build(): BiomeAreaSave = getSave(worldName)
    }
}
