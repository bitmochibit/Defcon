package com.mochibit.defcon.enums

enum class ItemBehaviour() {
    BLOCK,
    GENERIC,
    GAS_MASK,
    GAS_MASK_FILTER,
    RADIATION_INHIBITOR,
    GEIGER_COUNTER,
    WRENCH;

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
