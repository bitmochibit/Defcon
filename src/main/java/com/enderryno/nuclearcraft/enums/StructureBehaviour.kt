package com.enderryno.nuclearcraft.enums

import com.enderryno.nuclearcraft.classes.structures.Warhead
import com.enderryno.nuclearcraft.interfaces.PluginStructure

enum class StructureBehaviour(name: String) {
    WARHEAD("WARHEAD");

    val structureClass: Class<out PluginStructure?>?
        get() = when (this) {
            WARHEAD -> Warhead::class.java
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
