package com.mochibit.nuclearcraft.classes.structures
import com.mochibit.nuclearcraft.classes.StructureBlock
import com.mochibit.nuclearcraft.enums.BlockDataKey
import com.mochibit.nuclearcraft.enums.StructureBehaviour
import com.mochibit.nuclearcraft.interfaces.StructureDefinition
import com.mochibit.nuclearcraft.utils.MetaManager
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
