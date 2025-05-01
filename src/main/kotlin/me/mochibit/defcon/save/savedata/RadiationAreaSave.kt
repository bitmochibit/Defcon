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

import me.mochibit.defcon.radiation.RadiationArea
import me.mochibit.defcon.save.AbstractSaveData
import me.mochibit.defcon.save.schemas.RadiationSaveSchema
import me.mochibit.defcon.save.schemas.toRadiationArea
import me.mochibit.defcon.save.schemas.toSchema
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@SaveDataInfo("radiation_areas", 50)
class RadiationAreaSave private constructor(private val worldName: String) :
    AbstractSaveData<RadiationSaveSchema>(RadiationSaveSchema(), true) {

    private val maxId = AtomicInteger(0)

    init {
        setSuffixSupplier { "-$worldName" }
        maxId.set(getMaxId())
    }

    companion object {
        private val saves = ConcurrentHashMap<String, RadiationAreaSave>()

        fun getSave(worldName: String): RadiationAreaSave {
            return saves.computeIfAbsent(worldName) { name ->
                RadiationAreaSave(name)
            }
        }
    }

    fun addRadiationArea(area: RadiationArea): RadiationArea {
        val page = findAvailablePage()
        currentPage = page
        load()

        val newId = maxId.incrementAndGet()
        val indexedArea = area.copy(id = newId)
        schema.radiationAreas.add(indexedArea.toSchema())
        save()

        return indexedArea
    }

    fun getAll(): Set<RadiationArea> {
        return getAllPages().flatMapTo(HashSet()) { page ->
            getSchema(page)?.radiationAreas?.map { it.toRadiationArea() } ?: emptyList()
        }
    }

    fun get(id: Int): Pair<RadiationArea, Int>? {
        getAllPages().forEach { page ->
            val schema = getSchema(page) ?: return@forEach
            val area = schema.radiationAreas.find { it.id == id }
            if (area != null) {
                return Pair(area.toRadiationArea(), page)
            }
        }
        return null
    }

    fun delete(id: Int): Boolean {
        val pair = get(id) ?: return false
        val (area, page) = pair

        val schema = getSchema(page) ?: return false
        val removed = schema.radiationAreas.remove(area.toSchema())
        if (removed) {
            saveSchema(schema, page)
        }
        return removed
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