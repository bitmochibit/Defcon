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

package me.mochibit.defcon.save.savedata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.mochibit.defcon.radiation.RadiationArea
import me.mochibit.defcon.save.AbstractSaveData
import me.mochibit.defcon.save.schemas.RadiationSaveSchema
import me.mochibit.defcon.save.schemas.toRadiationArea
import me.mochibit.defcon.save.schemas.toSchema
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@SaveDataInfo("radiation_areas", maxPerFile = 50)
class RadiationAreaSave private constructor(private val worldName: String) :
    AbstractSaveData<RadiationSaveSchema>(RadiationSaveSchema(), true) {

    private val maxId = AtomicInteger(0)

    init {
        setSuffixSupplier { "-$worldName" }
        // Initialize maxId in a non-blocking way but wait for completion
        runBlocking {
            maxId.set(getMaxId())
        }
    }

    companion object {
        private val saves = ConcurrentHashMap<String, RadiationAreaSave>()

        fun getSave(worldName: String): RadiationAreaSave {
            return saves.computeIfAbsent(worldName) { name ->
                RadiationAreaSave(name)
            }
        }
    }

    /**
     * Adds a new radiation area
     */
    suspend fun addRadiationArea(area: RadiationArea): RadiationArea = withContext(Dispatchers.IO) {
        val page = findAvailablePage()
        currentPage = page
        load()

        val newId = maxId.incrementAndGet()
        val indexedArea = area.copy(id = newId)
        schema.radiationAreas.add(indexedArea.toSchema())
        save()

        return@withContext indexedArea
    }

    /**
     * Gets all radiation areas across all pages
     */
    suspend fun getAll(): Set<RadiationArea> = withContext(Dispatchers.IO) {
        return@withContext getAllPages().flatMapTo(HashSet()) { page ->
            getSchema(page)?.radiationAreas?.map { it.toRadiationArea() } ?: emptyList()
        }
    }

    /**
     * Gets a radiation area by ID along with its page number
     */
    suspend fun get(id: Int): Pair<RadiationArea, Int>? = withContext(Dispatchers.IO) {
        getAllPages().forEach { page ->
            val schema = getSchema(page) ?: return@forEach
            val area = schema.radiationAreas.find { it.id == id }
            if (area != null) {
                return@withContext Pair(area.toRadiationArea(), page)
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

        val removed = schema.radiationAreas.remove(area.toSchema())
        if (removed) {
            saveSchema(schema, page)
        }
        return@withContext removed
    }

    /**
     * Builder for RadiationAreaSave
     */
    class Builder : AbstractSaveData.Builder<RadiationSaveSchema, RadiationAreaSave>() {
        private var worldName: String = "world"

        fun forWorld(worldName: String): Builder {
            this.worldName = worldName
            return this
        }

        override fun build(): RadiationAreaSave {
            return getSave(worldName)
        }
    }
}