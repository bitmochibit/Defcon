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

package me.mochibit.defcon.extensions

import me.mochibit.defcon.save.savedata.PlayerDataSave
import org.bukkit.entity.Player

fun Player.getRadiationLevel(): Double {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    return playerData.getRadiationLevel()
}

fun Player.setRadiationLevel(radiationLevel: Double) {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    playerData.setRadiationLevel(radiationLevel)
}

fun Player.increaseRadiationLevel(double: Double): Double {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    return playerData.increaseRadiationLevel(double)
}

fun Player.decreaseRadiationLevel(double: Double): Double {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    return playerData.decreaseRadiationLevel(double)
}

fun Player.resetRadiationLevel() {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    playerData.resetRadiationLevel()
}

fun Player.unloadPlayerData() {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    playerData.unload()
}

fun Player.savePlayerData() {
    val playerData = PlayerDataSave(this.uniqueId.toString())
    playerData.save()
}
