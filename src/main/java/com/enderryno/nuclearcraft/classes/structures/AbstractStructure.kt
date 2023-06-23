package com.enderryno.nuclearcraft.classes.structures
import com.enderryno.nuclearcraft.classes.StructureBlock
import com.enderryno.nuclearcraft.enums.StructureBehaviour
import com.enderryno.nuclearcraft.interfaces.PluginStructure

abstract class AbstractStructure : PluginStructure {
    final override var structureBehaviour: StructureBehaviour? = null
    final override var structureBlocks: MutableList<StructureBlock?> = mutableListOf()
    final override var requiredInterface: Boolean = false
}
