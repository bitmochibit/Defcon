package com.mochibit.defcon.save.savedata

import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.AbstractSaveData
import com.mochibit.defcon.save.schemas.RadiationAreaSchema
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@SaveDataInfo("radiation_areas")
object RadiationAreaSave : AbstractSaveData<RadiationAreaSchema>(RadiationAreaSchema()) {
    private val lock = ReentrantLock()
    fun addRadiationArea(radiationArea: RadiationArea): RadiationArea {
        lock.withLock {
            this.load()
            val id = saveSchema.radiationAreas.maxByOrNull { it.id }?.id ?: 0
            val indexedRadiationArea = radiationArea.copy(id = id + 1)
            saveSchema.radiationAreas.add(indexedRadiationArea)
            this.save()
            return indexedRadiationArea
        }
    }

    fun getAll(): Set<RadiationArea> { // Load the data
        this.load()
        return saveSchema.radiationAreas.toSet()
    }

    fun get(id: Int): RadiationArea? {
        this.load()
        return saveSchema.radiationAreas.find { it.id == id }
    }

    fun delete(id: Int) {
        lock.withLock {
            val areaToRemove = get(id) ?: return
            saveSchema.radiationAreas.remove(areaToRemove)
            this.save()
        }
    }

}