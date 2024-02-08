package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.classes.StructureBlock
import org.bukkit.Location


interface PluginStructure {
    var id: String
    var structureBlocks: MutableList<StructureBlock>
    var requiredInterface: Boolean

    fun instantiateToWorld(locations: List<Location>)
    fun removeStructureFromWorld(locations: List<Location>)

}
