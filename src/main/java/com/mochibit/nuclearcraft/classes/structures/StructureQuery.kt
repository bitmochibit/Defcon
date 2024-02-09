package com.mochibit.nuclearcraft.classes.structures

import com.mochibit.nuclearcraft.interfaces.PluginStructure
import org.bukkit.Location

data class StructureQuery(val structures: List<PluginStructure>, val appliedLocations: List<Location>)
