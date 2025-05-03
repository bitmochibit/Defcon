package me.mochibit.defcon.effects

import org.bukkit.Color
import org.bukkit.Location

interface ColorSuppliable {
    val colorSupplier: () -> Color
}