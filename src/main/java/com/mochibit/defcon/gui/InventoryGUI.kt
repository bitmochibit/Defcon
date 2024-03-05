package com.mochibit.defcon.gui

import org.bukkit.entity.Player

interface InventoryGUI {
    val inventoryName: String
    val inventorySize: Int

    fun open(player: Player)
    fun close(player: Player)

}