package com.enderryno.nuclearcraft.classes.structures;

import com.enderryno.nuclearcraft.classes.StructureBlock;
import com.enderryno.nuclearcraft.enums.StructureBehaviour;
import com.enderryno.nuclearcraft.interfaces.PluginStructure;

import java.util.List;

public abstract class AbstractStructure implements PluginStructure {
    private Boolean requiredInterface;
    private StructureBehaviour structureBehaviour;
    private List<StructureBlock<?>> structureBlocks;
    private List<StructureBlock<?>> interfaceBlocks;

    @Override
    public PluginStructure setRequiredInterface(Boolean required) {
        this.requiredInterface = required;
        return this;
    }

    @Override
    public PluginStructure setStructureBehaviour(StructureBehaviour behaviour) {
        this.structureBehaviour = behaviour;
        return this;
    }

    @Override
    public List<StructureBlock<?>> getStructureBlocks() {
        return this.structureBlocks;
    }

    @Override
    public List<StructureBlock<?>> getInterfaceBlocks() {
        return this.interfaceBlocks;
    }

    @Override
    public Boolean requiresInterface() {
        return this.requiredInterface;
    }

    @Override
    public StructureBehaviour getStructureBehaviour() {
        return this.structureBehaviour;
    }
}
