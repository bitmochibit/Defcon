package com.mochibit.defcon.classes.structures

import com.mochibit.defcon.interfaces.StructureDefinition
import org.bukkit.Location

data class StructureQuery(val structures: List<StructureDefinition>, val appliedLocations: List<Location>)
