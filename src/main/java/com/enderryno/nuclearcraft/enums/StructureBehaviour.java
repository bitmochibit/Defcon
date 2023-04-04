package com.enderryno.nuclearcraft.enums;

import com.enderryno.nuclearcraft.classes.structures.Warhead;
import com.enderryno.nuclearcraft.interfaces.PluginStructure;
import org.apache.maven.model.Plugin;

public enum StructureBehaviour {
    WARHEAD("WARHEAD"),
    ;

    final String name;
    StructureBehaviour(String name) {
        this.name = name;
    }

    public static StructureBehaviour fromString(String text) {
        for (StructureBehaviour b : StructureBehaviour.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }

    public Class<? extends PluginStructure> getStructureClass() {
        switch (this) {
            case WARHEAD:
                return Warhead.class;
            default:
                return null;
        }
    }

    public String getName() {
        return name;
    }
}


