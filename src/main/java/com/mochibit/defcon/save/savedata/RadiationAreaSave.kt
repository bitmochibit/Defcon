package com.mochibit.defcon.save.savedata

import com.mochibit.defcon.save.AbstractSaveData
import com.mochibit.defcon.save.schemas.RadiationAreaSchema

@SaveDataInfo("radiation_areas")
class RadiationAreaSave : AbstractSaveData<RadiationAreaSchema>() {
    init { saveData = RadiationAreaSchema() }
}