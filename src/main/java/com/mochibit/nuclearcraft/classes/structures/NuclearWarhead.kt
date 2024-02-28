package com.mochibit.nuclearcraft.classes.structures

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.effects.NuclearMushroom
import com.mochibit.nuclearcraft.explosions.NuclearComponent
import com.mochibit.nuclearcraft.explosions.NuclearExplosion
import com.mochibit.nuclearcraft.interfaces.ExplodingStructure
import org.bukkit.Location
import org.bukkit.Material


class NuclearWarhead : AbstractStructureDefinition(), ExplodingStructure {
    override fun explode(center: Location, nuclearComponent: NuclearComponent) {
        NuclearExplosion(center, nuclearComponent).explode()
    }

}
