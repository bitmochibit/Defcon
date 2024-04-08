package com.mochibit.defcon.enums

import com.mochibit.defcon.Defcon
import org.bukkit.NamespacedKey

enum class BlockDataKey(val key: NamespacedKey) {
    CustomBlockId(NamespacedKey(Defcon.instance, "definitions-block-id")),
    ItemId(NamespacedKey(Defcon.instance, "item-id")),
    StructureId(NamespacedKey(Defcon.instance, "structure-id")),
    RadiationLevel(NamespacedKey(Defcon.instance, "radiation-level")),
}