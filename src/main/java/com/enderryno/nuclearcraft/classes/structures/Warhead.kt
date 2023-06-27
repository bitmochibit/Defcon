package com.enderryno.nuclearcraft.classes.structures

import com.enderryno.nuclearcraft.classes.ExplosiveComponent
import com.enderryno.nuclearcraft.interfaces.ExplodingStructure

class Warhead(override val explosiveComponent: ExplosiveComponent) : GenericStructure(), ExplodingStructure {

    override fun explode() {
        TODO("Not yet implemented")
    }

}
