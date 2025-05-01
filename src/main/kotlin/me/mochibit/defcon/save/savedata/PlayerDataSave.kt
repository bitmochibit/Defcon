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

import me.mochibit.defcon.player.PlayerData
import me.mochibit.defcon.save.AbstractSaveData
import me.mochibit.defcon.save.schemas.PlayerSaveSchema
import me.mochibit.defcon.save.schemas.toPlayerData
import me.mochibit.defcon.save.schemas.toSchema
import org.bukkit.entity.Player
import java.util.*
import kotlin.collections.HashSet

@SaveDataInfo("player_data", maxPerFile = 100)
class PlayerDataSave private constructor() :
    AbstractSaveData<PlayerSaveSchema>(PlayerSaveSchema(), true) {

    companion object {
        private val _instance by lazy { PlayerDataSave() }

        fun getInstance(): PlayerDataSave {
            return _instance
        }
    }

    /**
     * Saves player radiation data
     */
    fun savePlayerData(player: Player, radiationLevel: Double) {
        val playerUUID = player.uniqueId.toString()
        val page = findPageContainingPlayer(playerUUID) ?: findAvailablePage()
        currentPage = page
        load()

        // Find existing player data or create new entry
        val existingPlayer = schema.playersData.find { it.playerUUID == playerUUID }
        if (existingPlayer != null) {
            // Update existing player data
            existingPlayer.radiationLevel = radiationLevel
        } else {
            // Add new player data
            schema.playersData.add(PlayerData(player, radiationLevel).toSchema())
        }

        save()
    }

    /**
     * Gets player data by UUID
     */
    fun getPlayerData(player: Player): PlayerData? {
        val playerUUID = player.uniqueId.toString()
        val page = findPageContainingPlayer(playerUUID) ?: return null
        val schema = getSchema(page) ?: return null
        return schema.playersData.find { it.playerUUID == playerUUID }?.toPlayerData()
    }


    /**
     * Gets all player data across all pages
     */
    fun getAllPlayerData(): Set<PlayerData> {
        return getAllPages().flatMapTo(HashSet()) { page ->
            getSchema(page)?.playersData?.map { it.toPlayerData() } ?: emptySet()
        }
    }

    /**
     * Deletes player data
     */
    fun deletePlayerData(playerUUID: String): Boolean {
        val page = findPageContainingPlayer(playerUUID) ?: return false
        val schema = getSchema(page) ?: return false

        val removed = schema.playersData.removeIf { it.playerUUID == playerUUID }
        if (removed) {
            saveSchema(schema, page)
        }
        return removed
    }

    /**
     * Updates multiple fields for a player
     */
    fun updatePlayerData(playerData: PlayerData): Boolean {
        val playerUUID = playerData.player.uniqueId

        val page = findPageContainingPlayer(playerUUID.toString()) ?: return false
        val schema = getSchema(page) ?: return false

        val existing = schema.playersData.find { it.playerUUID == playerUUID.toString() }
        if (existing != null) {
            schema.playersData.remove(existing)
            schema.playersData.add(playerData.toSchema())
            saveSchema(schema, page)
            return true
        }
        return false
    }

    /**
     * Finds the page containing player data for the given UUID
     */
    private fun findPageContainingPlayer(playerUUID: String): Int? {
        getAllPages().forEach { page ->
            val schema = getSchema(page)
            if (schema?.playersData?.any { it.playerUUID == playerUUID } == true) {
                return page
            }
        }
        return null
    }

    /**
     * Builder for PlayerDataSave
     */
    class Builder : AbstractSaveData.Builder<PlayerSaveSchema, PlayerDataSave>() {
        override fun build(): PlayerDataSave {
            return getInstance()
        }
    }
}
