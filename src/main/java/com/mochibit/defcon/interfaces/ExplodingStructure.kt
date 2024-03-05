package com.mochibit.defcon.interfaces

import com.mochibit.defcon.explosions.NuclearComponent
import org.bukkit.Location

interface ExplodingStructure {
    fun explode(center: Location, nuclearComponent: NuclearComponent);
}