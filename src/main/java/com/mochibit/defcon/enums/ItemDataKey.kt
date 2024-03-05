package com.mochibit.defcon.enums

import com.mochibit.defcon.Defcon
import org.bukkit.NamespacedKey

enum class ItemDataKey(val key: NamespacedKey) {
    ItemID(NamespacedKey(Defcon.namespace, "item-id")),
    StackSize(NamespacedKey(Defcon.namespace, "item-stack-size")),
    Usable(NamespacedKey(Defcon.namespace, "item-usable")),
    Equipable(NamespacedKey(Defcon.namespace, "item-equipable")),
    Droppable(NamespacedKey(Defcon.namespace, "item-droppable")),
    Transportable(NamespacedKey(Defcon.namespace, "item-transportable")),
    Behaviour(NamespacedKey(Defcon.namespace, "item-behaviour")),
    CustomBlockId(NamespacedKey(Defcon.namespace, "custom-block-id")),
}