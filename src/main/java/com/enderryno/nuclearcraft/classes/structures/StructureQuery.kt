package com.enderryno.nuclearcraft.classes.structures

import com.enderryno.nuclearcraft.interfaces.PluginStructure
import org.bukkit.Location

data class StructureQuery(val structures: List<PluginStructure>, val appliedLocations: List<Location>)
