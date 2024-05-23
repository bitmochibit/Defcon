package com.mochibit.defcon.extensions

import com.mochibit.defcon.save.savedata.PlayerDataSave
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
