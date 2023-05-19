package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.classes.StructureBlock
import com.enderryno.nuclearcraft.enums.StructureBehaviour

interface PluginStructure {
    fun setRequiredInterface(required: Boolean?): PluginStructure
    fun setStructureBehaviour(behaviour: StructureBehaviour?): PluginStructure
    val structureBlocks: MutableList<StructureBlock>?
    val interfaceBlocks: MutableList<StructureBlock>?
    fun requiresInterface(): Boolean?
    val structureBehaviour: StructureBehaviour?
}
