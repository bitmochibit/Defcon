package com.enderryno.nuclearcraft.classes.structures

import com.enderryno.nuclearcraft.classes.StructureBlock
import com.enderryno.nuclearcraft.enums.StructureBehaviour
import com.enderryno.nuclearcraft.interfaces.PluginStructure

abstract class AbstractStructure : PluginStructure {
    private var requiredInterface: Boolean? = null
    final override var structureBehaviour: StructureBehaviour? = null
        private set
    override val structureBlocks: MutableList<StructureBlock>? = null

    //@todo: - Remove this fucking shit please, bombastic side eye, and add interface property directly to StructureBlock
    override val interfaceBlocks: MutableList<StructureBlock>? = null
    override fun setRequiredInterface(required: Boolean?): PluginStructure {
        requiredInterface = required
        return this
    }

    override fun setStructureBehaviour(behaviour: StructureBehaviour?): PluginStructure {
        structureBehaviour = behaviour
        return this
    }

    override fun requiresInterface(): Boolean? {
        return requiredInterface
    }
}
