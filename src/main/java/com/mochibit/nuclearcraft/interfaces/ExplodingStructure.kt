package com.mochibit.nuclearcraft.interfaces

import com.mochibit.nuclearcraft.explosives.NuclearComponent
import org.bukkit.Location

interface ExplodingStructure {
    fun explode(center: Location, nuclearComponent: NuclearComponent);
}