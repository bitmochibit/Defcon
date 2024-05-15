package com.mochibit.defcon.save.savedata

import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.AbstractSaveData
import com.mochibit.defcon.save.schemas.RadiationAreaSchema
import java.util.concurrent.atomic.AtomicInteger

@SaveDataInfo("radiation_areas")
class RadiationAreaSave : AbstractSaveData<RadiationAreaSchema>() {
    init { super.saveData = RadiationAreaSchema() }

    fun addRadiationArea(radiationArea: RadiationArea): RadiationArea {
        this.load()
        val id = saveData.radiationAreas.maxByOrNull { it.id }?.id ?: 1
        val indexedRadiationArea = radiationArea.copy(id = id + 1)
        saveData.radiationAreas.add(radiationArea)
        this.save()
        return indexedRadiationArea
    }

    fun getAll(): Set<RadiationArea> { // Load the data
        this.load()
        return saveData.radiationAreas.toSet()
    }

    fun get(id: Int): RadiationArea? {
        this.load()
        return saveData.radiationAreas.find { it.id == id }
    }

    fun delete(data: RadiationArea) {
        this.load()
        saveData.radiationAreas.remove(data)
        this.save()
    }

}