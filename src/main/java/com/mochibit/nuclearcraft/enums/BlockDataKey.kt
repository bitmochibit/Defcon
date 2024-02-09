package com.mochibit.nuclearcraft.enums

import com.mochibit.nuclearcraft.NuclearCraft
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

enum class BlockDataKey(val key: NamespacedKey) {
    CustomBlockId(NamespacedKey(NuclearCraft.instance!!, "custom-block-id")),
    ItemId(NamespacedKey(NuclearCraft.instance!!, "item-id")),
    StructureId(NamespacedKey(NuclearCraft.instance!!, "structure-id")),

}