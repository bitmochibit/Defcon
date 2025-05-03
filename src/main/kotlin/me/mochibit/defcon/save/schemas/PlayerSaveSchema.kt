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

import me.mochibit.defcon.player.PlayerData
import org.bukkit.Bukkit


data class PlayerSaveSchema(
    var playersData: HashSet<PlayerDataSchema> = HashSet()
) : SaveSchema {
    data class PlayerDataSchema(
        var playerUUID: String,
        var radiationLevel: Double = 0.0,
    )

    override fun getMaxID(): Int {
        return playersData.maxOfOrNull { it.playerUUID.hashCode() } ?: 0
    }

    override fun getSize(): Int {
        return playersData.size
    }

    override fun getAllItems(): List<Any> {
        return playersData.toList()
    }
}

fun PlayerData.toSchema(): PlayerSaveSchema.PlayerDataSchema {
    return PlayerSaveSchema.PlayerDataSchema(
        playerUUID = this.player.uniqueId.toString(),
        radiationLevel = this.radiationLevel
    )
}

fun PlayerSaveSchema.PlayerDataSchema.toPlayerData(): PlayerData {
    val player = Bukkit.getPlayer(java.util.UUID.fromString(playerUUID)) ?: throw IllegalArgumentException("Player with UUID $playerUUID not found")
    return PlayerData(player, radiationLevel)
}