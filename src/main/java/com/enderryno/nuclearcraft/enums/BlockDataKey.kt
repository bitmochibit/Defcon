package com.enderryno.nuclearcraft.enums

import com.enderryno.nuclearcraft.NuclearCraft
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

enum class BlockDataKey(val key: NamespacedKey) {
    CustomBlockId(NamespacedKey(NuclearCraft.instance!!, "custom-block-id")),
    StructureId(NamespacedKey(NuclearCraft.instance!!, "structure-id")),

}