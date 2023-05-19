package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.enums.ItemBehaviour
import org.bukkit.inventory.ItemStack

interface PluginItem {
    // Base properties
    fun setName(name: String?): PluginItem
    fun setDescription(description: String?): PluginItem
    fun setID(id: String?): PluginItem
    fun setMinecraftId(minecraftId: String?): PluginItem
    val name: String?
    val displayName: String?
    val description: String?
    val id: String?
    val minecraftId: String?
    val itemStack: ItemStack

    // Characteristics
    fun setModelId(modelId: Int): PluginItem
    fun setCustomBlockId(customBlockId: String?): PluginItem
    fun setUsable(usable: Boolean): PluginItem
    fun setEquipable(equipable: Boolean): PluginItem
    fun setDroppable(droppable: Boolean): PluginItem
    fun setStackSize(stackSize: Int): PluginItem
    fun setTransportable(transportable: Boolean): PluginItem
    val modelId: Int
    val customBlockId: String?
    val isUsable: Boolean
    val isEquipable: Boolean
    val isDroppable: Boolean
    val stackSize: Int
    val isTransportable: Boolean

    /*Behaviour type*/
    fun setBehaviour(behaviour: ItemBehaviour?): PluginItem
    val behaviour: ItemBehaviour?
}
