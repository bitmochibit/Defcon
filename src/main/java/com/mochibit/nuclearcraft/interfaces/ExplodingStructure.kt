package com.mochibit.nuclearcraft.interfaces

import com.mochibit.nuclearcraft.explosions.NuclearComponent
import org.bukkit.Location

interface ExplodingStructure {
    fun explode(center: Location, nuclearComponent: NuclearComponent);
}