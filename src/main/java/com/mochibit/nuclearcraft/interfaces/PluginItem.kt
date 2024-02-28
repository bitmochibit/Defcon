package com.mochibit.nuclearcraft.interfaces

import com.mochibit.nuclearcraft.enums.ItemBehaviour
import org.bukkit.inventory.ItemStack

interface PluginItem {
    // Identification
    val id: String
    val name: String
    val displayName: String
    val description: String?
    val minecraftId: String
    val itemStack: ItemStack

    // Characteristics
    val modelId: Int
    val customBlockId: String?
    val isUsable: Boolean
    val isEquipable: Boolean
    val isDroppable: Boolean
    val stackSize: Int
    val isTransportable: Boolean

    /*Behaviour type*/
    val behaviour: ItemBehaviour
}
