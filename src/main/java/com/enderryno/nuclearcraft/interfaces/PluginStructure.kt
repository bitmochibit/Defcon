package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.classes.StructureBlock
import com.enderryno.nuclearcraft.enums.StructureBehaviour

interface PluginStructure {
    var structureBlocks: MutableList<StructureBlock?>
    var structureBehaviour: StructureBehaviour?
    var requiredInterface: Boolean
}
