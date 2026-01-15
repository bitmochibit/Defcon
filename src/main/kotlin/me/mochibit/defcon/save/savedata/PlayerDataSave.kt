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
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import me.mochibit.defcon.player.PlayerData
import me.mochibit.defcon.save.AbstractSaveData
import me.mochibit.defcon.save.schemas.PlayerSaveSchema
import me.mochibit.defcon.save.schemas.toPlayerData
import me.mochibit.defcon.save.schemas.toSchema
import org.bukkit.entity.Player

@SaveDataInfo("player_data", maxPerFile = 100)
class PlayerDataSave private constructor(
    useCache: Boolean = true,
) : AbstractSaveData<PlayerSaveSchema>(
        schemaSerializer = serializer(),
        createEmptySchema = { PlayerSaveSchema() },
        useCache = useCache,
    ) {
    companion object {
        private val _instance by lazy { PlayerDataSave() }

        fun getInstance(): PlayerDataSave = _instance
    }

    /**
     * Saves player radiation data
     */
    suspend fun savePlayerData(
        player: Player,
        radiationLevel: Double,
    ): Unit =
        withContext(Dispatchers.IO) {
            val playerUUID = player.uniqueId.toString()
            val page = findPageContainingPlayer(playerUUID) ?: findAvailablePage()
            currentPage = page
            val schema = load()

            // Find existing player data or create new entry
            val existingPlayer = schema.playersData.find { it.playerUUID == playerUUID }
            if (existingPlayer != null) {
                // Update existing player data
                existingPlayer.radiationLevel = radiationLevel
            } else {
                // Add new player data
                schema.playersData.add(PlayerData(player, radiationLevel).toSchema())
            }

            save(schema)
        }

    /**
     * Gets player data by UUID
     */
    suspend fun getPlayerData(player: Player): PlayerData? =
        withContext(Dispatchers.IO) {
            val playerUUID = player.uniqueId.toString()
            val page = findPageContainingPlayer(playerUUID) ?: return@withContext null
            val schema = getSchema(page) ?: return@withContext null
            schema.playersData.find { it.playerUUID == playerUUID }?.toPlayerData()
        }

    /**
     * Gets all player data across all pages
     */
    suspend fun getAllPlayerData(): Set<PlayerData> =
        withContext(Dispatchers.IO) {
            getAllPages().flatMapTo(HashSet()) { page ->
                getSchema(page)?.playersData?.mapNotNull {
                    try {
                        it.toPlayerData()
                    } catch (e: IllegalArgumentException) {
                        null // Player not found, skip
                    }
                } ?: emptySet()
            }
        }

    /**
     * Deletes player data
     */
    suspend fun deletePlayerData(playerUUID: String): Boolean =
        withContext(Dispatchers.IO) {
            val page = findPageContainingPlayer(playerUUID) ?: return@withContext false
            val schema = getSchema(page) ?: return@withContext false

            val removed = schema.playersData.removeIf { it.playerUUID == playerUUID }
            if (removed) {
                saveSchema(schema, page)
            }
            removed
        }

    /**
     * Updates multiple fields for a player
     */
    suspend fun updatePlayerData(playerData: PlayerData): Boolean =
        withContext(Dispatchers.IO) {
            val playerUUID = playerData.player.uniqueId.toString()
            val page = findPageContainingPlayer(playerUUID) ?: return@withContext false
            val schema = getSchema(page) ?: return@withContext false

            val existing = schema.playersData.find { it.playerUUID == playerUUID }
            if (existing != null) {
                schema.playersData.remove(existing)
                schema.playersData.add(playerData.toSchema())
                saveSchema(schema, page)
                true
            } else {
                false
            }
        }

    /**
     * Finds the page containing player data for the given UUID
     */
    private suspend fun findPageContainingPlayer(playerUUID: String): Int? =
        withContext(Dispatchers.IO) {
            for (page in getAllPages()) {
                val schema = getSchema(page)
                if (schema?.playersData?.any { it.playerUUID == playerUUID } == true) {
                    return@withContext page
                }
            }
            null
        }

    /**
     * Builder for PlayerDataSave
     */
    class Builder : AbstractSaveData.Builder<PlayerSaveSchema, PlayerDataSave>() {
        override fun build(): PlayerDataSave = getInstance()
    }
}
