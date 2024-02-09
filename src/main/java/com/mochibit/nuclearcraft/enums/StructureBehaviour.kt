package com.mochibit.nuclearcraft.enums

import com.mochibit.nuclearcraft.classes.structures.Bomb
import com.mochibit.nuclearcraft.interfaces.PluginStructure


enum class StructureBehaviour(name: String) {
    BOMB("BOMB");

    val structureClass: Class<out PluginStructure?>?
        get() = when (this) {
            BOMB -> Bomb::class.java
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
