package com.mochibit.defcon.interfaces

import com.mochibit.defcon.classes.StructureBlock
import com.mochibit.defcon.enums.StructureBehaviour
import org.bukkit.Location


interface StructureDefinition {
    var id: String
    var structureBlocks: MutableList<StructureBlock>
    var structureBehaviour: StructureBehaviour?
    var requiredInterface: Boolean

    fun saveToWorld(locations: List<Location>)
    fun removeStructureFromWorld(locations: List<Location>)

}
