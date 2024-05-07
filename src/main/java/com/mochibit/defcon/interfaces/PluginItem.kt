package com.mochibit.defcon.interfaces

import com.mochibit.defcon.enums.ItemBehaviour
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
    val equipSlotNumber: Int
    val isDroppable: Boolean
    val stackSize: Int
    val isTransportable: Boolean

    /*Behaviour type*/
    val behaviour: ItemBehaviour
}
