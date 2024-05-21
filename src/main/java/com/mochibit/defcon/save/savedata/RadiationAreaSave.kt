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
        propertySupplier = Supplier { worldName }
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
            info("CACHED MAX ID: ${cachedMaxId.get()}")
            info("Loading radiation areas")
            while (pageExists()) {
                this.load()
                info(schema.radiationAreas.size.toString())
                if (schema.radiationAreas.size < maxPerFile) {
                    info("Found a save file with less than $maxPerFile radiation areas")
                    break
                }
                info("Moving to next page")
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
            while (pageExists()) {
                this.load()
                allRadiationAreas.addAll(load()?.radiationAreas ?: emptySet())
                nextPage()
            }
            this.setPage(0)

            return schema.radiationAreas.toSet()
        }
    }

    fun get(id: Int): RadiationArea? {
        lock.withLock {
            while (pageExists()) {
                this.load()
                val area = schema.radiationAreas.find { it.id == id }
                if (area != null) {
                    return area
                }
                nextPage()
            }
            return null
        }
    }

    fun delete(id: Int) {
        lock.withLock {
            val areaToRemove = get(id) ?: return
            schema.radiationAreas.remove(areaToRemove)
            this.save()
        }
    }

    private fun getMaximumId() {
        lock.withLock {
            var maxId = 0
            while (pageExists()) {
                this.load()
                val max = schema.radiationAreas.maxByOrNull { it.id }?.id ?: 0
                if (max > maxId) {
                    maxId = max
                }
                nextPage()
            }
            cachedMaxId.set(maxId)
            setPage(0)
            this.load()
        }
    }
}