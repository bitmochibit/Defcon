package com.mochibit.nuclearcraft.enums

import com.mochibit.nuclearcraft.NuclearCraft
import org.bukkit.NamespacedKey

enum class ItemDataKey(val key: NamespacedKey) {
    ItemID(NamespacedKey(NuclearCraft.namespace, "item-id")),
    StackSize(NamespacedKey(NuclearCraft.namespace, "item-stack-size")),
    Usable(NamespacedKey(NuclearCraft.namespace, "item-usable")),
    Equipable(NamespacedKey(NuclearCraft.namespace, "item-equipable")),
    Droppable(NamespacedKey(NuclearCraft.namespace, "item-droppable")),
    Transportable(NamespacedKey(NuclearCraft.namespace, "item-transportable")),
    Behaviour(NamespacedKey(NuclearCraft.namespace, "item-behaviour")),
    CustomBlockId(NamespacedKey(NuclearCraft.namespace, "custom-block-id")),
}