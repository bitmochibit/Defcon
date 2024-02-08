package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.explosives.ExplosiveComponent

interface ExplodingStructure {
    fun explode(explosiveComponent: ExplosiveComponent)
}