package com.mochibit.defcon.enums

enum class BlockBehaviour(name: String) {
    GENERIC("GENERIC"),
    STRUCTURE_COMPONENT("STRUCTURE_COMPONENT"),
    STRUCTURE_INTERFACE("STRUCTURE_INTERFACE");

    companion object {
        fun fromString(text: String?): BlockBehaviour? {
            for (b in values()) {
                if (b.name.equals(text, ignoreCase = true)) {
                    return b
                }
            }
            return null
        }
    }
}
