package com.mochibit.defcon.save.savedata

import com.mochibit.defcon.save.AbstractSaveData
import com.mochibit.defcon.save.FileSplitType
import com.mochibit.defcon.save.schemas.PlayerDataSchema
import java.util.concurrent.ConcurrentHashMap

@SaveDataInfo("player_data", "/player_data/")
object PlayerDataSave :
    AbstractSaveData<PlayerDataSchema>(PlayerDataSchema(), FileSplitType.PROPERTY) {

    private val cachedPlayerData = ConcurrentHashMap<String, PlayerDataSchema>()

    private fun getCacheOrLoad() : PlayerDataSchema {
        if (cachedPlayerData.containsKey(UUID)) {
            this.saveSchema = cachedPlayerData[UUID]!!
        } else {
            load()
            cachedPlayerData[UUID] = this.saveSchema
        }
        return this.saveSchema
    }
    fun unload() {
        getCacheOrLoad()
        save()
        cachedPlayerData.remove(UUID)
    }

    fun getRadiationLevel(): Double {
        return getCacheOrLoad().radiationLevel
    }

    fun setRadiationLevel(radiationLevel: Double) {
        getCacheOrLoad()
        this.saveSchema.radiationLevel = radiationLevel
        save()
        cachedPlayerData.remove(UUID)
    }

}



