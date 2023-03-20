package com.enderryno.nuclearcraft.enums;

public enum ItemBehaviour {
    GENERIC("generic"),
    GAS_MASK("gas_mask"),
    GAS_MASK_FILTER("gas_mask_filter"),
    RADIATION_INHIBITOR("radiation_inhibitor"),
    GEIGER_COUNTER("geiger_counter");

    private String name;

    ItemBehaviour(String name) {
        this.name = name;
    }

    public static ItemBehaviour fromString(String text) {
        for (ItemBehaviour b : ItemBehaviour.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }

    public String getName() {
        return this.name;
    }

}
