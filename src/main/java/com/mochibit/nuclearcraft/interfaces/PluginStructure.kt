package com.mochibit.nuclearcraft.interfaces

import com.mochibit.nuclearcraft.classes.StructureBlock
import com.mochibit.nuclearcraft.enums.StructureBehaviour
import org.bukkit.Location


interface PluginStructure {
    var id: String
    var structureBlocks: MutableList<StructureBlock>
    var structureBehaviour: StructureBehaviour?
    var requiredInterface: Boolean

    fun instantiateToWorld(locations: List<Location>)
    fun removeStructureFromWorld(locations: List<Location>)

}
