package com.mochibit.nuclearcraft.interfaces

import com.mochibit.nuclearcraft.explosives.ExplosiveComponent

interface ExplodingStructure {
    fun explode(explosiveComponent: ExplosiveComponent)
}