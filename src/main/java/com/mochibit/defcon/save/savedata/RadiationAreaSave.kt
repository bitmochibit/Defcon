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

package com.mochibit.defcon.save.savedata

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.AbstractSaveData
import com.mochibit.defcon.save.schemas.RadiationAreaSchema
import org.bukkit.World
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import kotlin.concurrent.withLock

@SaveDataInfo("radiation_areas")
class RadiationAreaSave private constructor(private val worldName: String) :
    AbstractSaveData<RadiationAreaSchema>(RadiationAreaSchema(),true) {
    private val lock: ReentrantLock = ReentrantLock()
    private val maxPerFile: Int = 50
    private var cachedMaxId = AtomicInteger(0)
    init {
        setSuffixSupplier { "-$worldName" }
        getMaximumId()
    }
    companion object {
        private val radiationAreaSaves = mutableMapOf<String, RadiationAreaSave>()
        fun getSave(world: World) : RadiationAreaSave {
            val worldName = world.name
            if (radiationAreaSaves.containsKey(worldName)) {
                return radiationAreaSaves[worldName]!!
            }
            val save = RadiationAreaSave(worldName)
            radiationAreaSaves[worldName] = save
            return save
        }
    }
    fun addRadiationArea(radiationArea: RadiationArea): RadiationArea {
        lock.withLock {
            // get the available save file which the list size is less than maxPerFile and get the max id from all files
            while (pageExists(currentPage ?: 0)) {
                this.load()
                if (schema.radiationAreas.size < maxPerFile) {
                    break
                }
                schema.radiationAreas.clear()
                nextPage()
            }
            val indexedRadiationArea = radiationArea.copy(id = cachedMaxId.get() + 1)
            schema.radiationAreas.add(indexedRadiationArea)
            this.save()
            cachedMaxId.incrementAndGet()
            return indexedRadiationArea
        }
    }

    fun getAll(): Set<RadiationArea> {
        lock.withLock {
            val allRadiationAreas = mutableSetOf<RadiationArea>()
            var page = 0
            while (pageExists(page)) {
                val schema = getSchema(page)
                if (schema == null) {
                    page++
                    continue
                }
                allRadiationAreas.addAll(schema.radiationAreas)
                page++
            }
            return allRadiationAreas
        }
    }

    fun get(id: Int): Pair<RadiationArea, Int>? {
        lock.withLock {
            var page = 0
            while (pageExists(page)) {
                val schema = getSchema(page)
                if (schema == null) {
                    page++
                    continue
                }
                val area = schema.radiationAreas.find { it.id == id }
                if (area != null) {
                    return Pair(area, page)
                }
                page++
            }
            return null
        }
    }

    fun delete(id: Int) {
        lock.withLock {
            val areaToRemove = get(id) ?: return
            val schema = getSchema(areaToRemove.second) ?: return
            schema.radiationAreas.remove(areaToRemove.first)
            this.saveSchema(schema, areaToRemove.second)
        }
    }

    private fun getMaximumId() {
        lock.withLock {
            var maxId = 0
            var page = 0
            while (pageExists(page)) {
                val schema = getSchema(page)
                if (schema == null) {
                    page++
                    continue
                }
                val max = schema.radiationAreas.maxByOrNull { it.id }?.id ?: 0
                if (max > maxId) {
                    maxId = max
                }
                page++
            }
            cachedMaxId.set(maxId)
        }
    }
}