package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.classes.StructureBlock;
import com.enderryno.nuclearcraft.enums.StructureBehaviour;

import java.util.List;

public interface PluginStructure {
    PluginStructure setRequiredInterface(Boolean required);
    PluginStructure setStructureBehaviour(StructureBehaviour behaviour);
    List<StructureBlock<?>> getStructureBlocks();
    List<StructureBlock<?>> getInterfaceBlocks();
    Boolean requiresInterface();
    StructureBehaviour getStructureBehaviour();
}
