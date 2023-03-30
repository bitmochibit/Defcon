package com.enderryno.nuclearcraft.enums;

import org.bukkit.block.Block;

public enum BlockBehaviour {
    GENERIC("GENERIC"),
    STRUCTURE_COMPONENT("STRUCTURE_COMPONENT"),
    STRUCTURE_INTERFACE("STRUCTURE_INTERFACE"),
    ;

    final String name;
    BlockBehaviour(String name) {
        this.name = name;
    }

    public static BlockBehaviour fromString(String text) {
        for (BlockBehaviour b : BlockBehaviour.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }
}


