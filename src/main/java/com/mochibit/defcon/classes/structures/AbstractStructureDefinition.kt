package com.mochibit.defcon.classes.structures
import com.mochibit.defcon.classes.StructureBlock
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.enums.StructureBehaviour
import com.mochibit.defcon.interfaces.StructureDefinition
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location

abstract class AbstractStructureDefinition : StructureDefinition {
    final override var id: String = ""
    final override var structureBehaviour: StructureBehaviour? = null
    final override var structureBlocks: MutableList<StructureBlock> = mutableListOf()
    final override var requiredInterface: Boolean = false

    override fun saveToWorld(locations: List<Location>) {
        // Save each location passed to the block meta at the specified location
        for (location in locations) {
            MetaManager.setBlockData(location, BlockDataKey.StructureId, id)
        }
    }

    override fun removeStructureFromWorld(locations: List<Location>) {
        // Remove the block meta at the specified location
        for (location in locations) {
            MetaManager.removeBlockData(location, BlockDataKey.StructureId)
        }
    }

}
