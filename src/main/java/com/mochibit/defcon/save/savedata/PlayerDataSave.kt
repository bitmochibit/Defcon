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
import com.mochibit.defcon.save.AbstractSaveData
import com.mochibit.defcon.save.schemas.PlayerDataSchema
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

@SaveDataInfo("player_data", "/player_data/")
class PlayerDataSave(val uuid: String) :
    AbstractSaveData<PlayerDataSchema>(PlayerDataSchema()) {
    companion object {
        private val cachedPlayerData = ConcurrentHashMap<String, PlayerDataSchema>()
    }
    init {
        setSuffixSupplier { "-$uuid" }
    }
    private fun getCacheOrLoad(): PlayerDataSchema {
        if (cachedPlayerData.containsKey(uuid)) {
            val cached = cachedPlayerData[uuid]
            if (cached != null) {
                schema = cached
                return schema
            }
        }
        load()
        cachedPlayerData[uuid] = schema

        return this.schema
    }
    fun unload() {
        getCacheOrLoad()
        save()
        cachedPlayerData.remove(uuid)
    }
    fun getRadiationLevel(): Double {
        return getCacheOrLoad().radiationLevel
    }
    fun setRadiationLevel(radiationLevel: Double) {
        getCacheOrLoad()
        this.schema.radiationLevel = radiationLevel
    }

    fun increaseRadiationLevel(double: Double): Double {
        getCacheOrLoad()
        this.schema.radiationLevel += double
        return this.schema.radiationLevel
    }

    fun decreaseRadiationLevel(double: Double): Double {
        getCacheOrLoad()
        this.schema.radiationLevel -= double
        return this.schema.radiationLevel
    }

    fun resetRadiationLevel() {
        val schemaTest = getCacheOrLoad()
        this.schema.radiationLevel = 0.0
        info(schemaTest.toString())
        info("VS")
        info(this.schema.toString())
    }
}





