package com.enderryno.nuclearcraft.classes.structures
import com.enderryno.nuclearcraft.classes.StructureBlock
import com.enderryno.nuclearcraft.enums.BlockDataKey
import com.enderryno.nuclearcraft.enums.StructureBehaviour
import com.enderryno.nuclearcraft.interfaces.PluginStructure
import com.enderryno.nuclearcraft.utils.MetaManager
import org.bukkit.Location

abstract class GenericStructure : PluginStructure {
    final override var id: String = ""
    final override var structureBehaviour: StructureBehaviour? = null
    final override var structureBlocks: MutableList<StructureBlock> = mutableListOf()
    final override var requiredInterface: Boolean = false

    override fun instantiateToWorld(locations: List<Location>) {
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
