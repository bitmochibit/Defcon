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

package me.mochibit.defcon.save.schemas

import kotlinx.serialization.Serializable
import me.mochibit.defcon.player.PlayerData
import org.bukkit.Bukkit
import java.util.UUID

@Serializable
data class PlayerSaveSchema(
    val playersData: HashSet<PlayerDataSchema> = HashSet(),
) : SaveSchema {
    @Serializable
    data class PlayerDataSchema(
        val playerUUID: String,
        var radiationLevel: Double = 0.0,
    )

    override fun getMaxID(): Int = playersData.maxOfOrNull { it.playerUUID.hashCode() } ?: 0

    override fun getSize(): Int = playersData.size

    override fun getAllItems(): List<Any> = playersData.toList()
}

fun PlayerData.toSchema(): PlayerSaveSchema.PlayerDataSchema =
    PlayerSaveSchema.PlayerDataSchema(
        playerUUID = player.uniqueId.toString(),
        radiationLevel = radiationLevel,
    )

fun PlayerSaveSchema.PlayerDataSchema.toPlayerData(): PlayerData {
    val player =
        Bukkit.getPlayer(UUID.fromString(playerUUID))
            ?: throw IllegalArgumentException("Player with UUID $playerUUID not found")
    return PlayerData(player, radiationLevel)
}
