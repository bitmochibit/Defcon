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
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.save.AbstractSaveData
import me.mochibit.defcon.save.schemas.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

//@TODO: This code needs a refactor to decrease the boilerplate

@SaveDataInfo("saved_biomes", maxPerFile = 50)
class BiomeAreaSave private constructor(private val worldName: String) :
    AbstractSaveData<BiomeAreaSaveSchema>(BiomeAreaSaveSchema(), true) {

    private val maxId = AtomicInteger(0)

    init {
        setSuffixSupplier { "-$worldName" }
        // Initialize maxId in a non-blocking way but wait for completion
        runBlocking {
            maxId.set(getMaxId())
        }
    }

    companion object {
        private val saves = ConcurrentHashMap<String, BiomeAreaSave>()

        fun getSave(worldName: String): BiomeAreaSave {
            return saves.computeIfAbsent(worldName) { name ->
                BiomeAreaSave(name)
            }
        }
    }

    /**
     * Adds a new biome area
     */
    suspend fun addBiome(biome: CustomBiomeHandler.CustomBiomeBoundary): CustomBiomeHandler.CustomBiomeBoundary = withContext(Dispatchers.IO) {
        val page = findAvailablePage()
        currentPage = page
        load()

        val newId = maxId.incrementAndGet()
        val indexedArea = biome.copy(id = newId)
        schema.biomeAreas.add(indexedArea.toSchema())
        save()

        return@withContext indexedArea
    }

    suspend fun updateBiome(biome: CustomBiomeHandler.CustomBiomeBoundary): Boolean = withContext(Dispatchers.IO) {
        val page = findAvailablePage()
        currentPage = page
        load()

        val existingArea = schema.biomeAreas.find { it.id == biome.id }
        if (existingArea != null) {
            schema.biomeAreas.remove(existingArea)
            schema.biomeAreas.add(biome.toSchema())
            save()
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Gets all biome boundaries across all pages
     */
    suspend fun getAll(): Set<CustomBiomeHandler.CustomBiomeBoundary> = withContext(Dispatchers.IO) {
        return@withContext getAllPages().flatMapTo(HashSet()) { page ->
            getSchema(page)?.biomeAreas?.map { it.toCustomBiomeBoundary() } ?: emptyList()
        }
    }

    /**
     * Gets a radiation area by ID along with its page number
     */
    suspend fun get(id: Int): Pair<CustomBiomeHandler.CustomBiomeBoundary, Int>? = withContext(Dispatchers.IO) {
        getAllPages().forEach { page ->
            val schema = getSchema(page) ?: return@forEach
            val area = schema.biomeAreas.find { it.id == id }
            if (area != null) {
                return@withContext Pair(area.toCustomBiomeBoundary(), page)
            }
        }
        return@withContext null
    }

    /**
     * Deletes a radiation area by ID
     */
    suspend fun delete(id: Int): Boolean = withContext(Dispatchers.IO) {
        val pair = get(id) ?: return@withContext false
        val (area, page) = pair
        val schema = getSchema(page) ?: return@withContext false

        val removed = schema.biomeAreas.remove(area.toSchema())
        if (removed) {
            saveSchema(schema, page)
        }
        return@withContext removed
    }

    /**
     * Builder for RadiationAreaSave
     */
    class Builder : AbstractSaveData.Builder<BiomeAreaSaveSchema, BiomeAreaSave>() {
        private var worldName: String = "world"

        fun forWorld(worldName: String): Builder {
            this.worldName = worldName
            return this
        }

        override fun build(): BiomeAreaSave {
            return getSave(worldName)
        }
    }
}