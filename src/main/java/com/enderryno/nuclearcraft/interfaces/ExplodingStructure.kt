package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.classes.ExplosiveComponent

interface ExplodingStructure {
    val explosiveComponent: ExplosiveComponent
    fun explode()
}