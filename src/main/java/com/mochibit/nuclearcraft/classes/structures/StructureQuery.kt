package com.mochibit.nuclearcraft.classes.structures

import com.mochibit.nuclearcraft.interfaces.StructureDefinition
import org.bukkit.Location

data class StructureQuery(val structures: List<StructureDefinition>, val appliedLocations: List<Location>)
