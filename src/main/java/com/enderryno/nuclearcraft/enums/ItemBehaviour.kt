package com.enderryno.nuclearcraft.enums

enum class ItemBehaviour(name: String) {
    BLOCK("block"),
    GENERIC("generic"),
    GAS_MASK("gas_mask"),
    GAS_MASK_FILTER("gas_mask_filter"),
    RADIATION_INHIBITOR("radiation_inhibitor"),
    GEIGER_COUNTER("geiger_counter");

    companion object {
        fun fromString(text: String?): ItemBehaviour? {
            for (b in values()) {
                if (b.name.equals(text, ignoreCase = true)) {
                    return b
                }
            }
            return null
        }
    }
}
