package com.mochibit.defcon.enums

import com.mochibit.defcon.classes.structures.NuclearWarhead
import com.mochibit.defcon.interfaces.StructureDefinition


enum class StructureBehaviour(name: String) {
    BOMB("BOMB");

    val structureClass: Class<out StructureDefinition?>?
        get() = when (this) {
            BOMB -> NuclearWarhead::class.java
            else -> null
        }

    companion object {
        fun fromString(text: String?): StructureBehaviour? {
            for (b in values()) {
                if (b.name.equals(text, ignoreCase = true)) {
                    return b
                }
            }
            return null
        }
    }
}
