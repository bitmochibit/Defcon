package com.mochibit.defcon.classes.structures

import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.explosions.NuclearExplosion
import com.mochibit.defcon.interfaces.ExplodingStructure
import org.bukkit.Location


class NuclearWarhead : AbstractStructureDefinition(), ExplodingStructure {
    override fun explode(center: Location, nuclearComponent: NuclearComponent) {
        NuclearExplosion(center, nuclearComponent).explode()
    }

}
