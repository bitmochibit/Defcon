package com.mochibit.defcon.extensions

import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.enums.ItemDataKey
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.inventory.ItemStack


fun ItemStack.getItemID(): String? {
    return this.getItemData<String>(ItemDataKey.ItemID)
}

fun ItemStack.getCustomStackSize(): Int? {
    return this.getItemData<Int>(ItemDataKey.StackSize)
}

fun ItemStack.isUsable(): Boolean {
    return this.getItemData<Boolean>(ItemDataKey.Usable) ?: false
}

fun ItemStack.isEquipable(): Boolean {
    return this.getItemData<Boolean>(ItemDataKey.Equipable) ?: false
}

fun ItemStack.isDroppable(): Boolean {
    return this.getItemData<Boolean>(ItemDataKey.Droppable) ?: false
}

fun ItemStack.isTransportable(): Boolean {
    return this.getItemData<Boolean>(ItemDataKey.Transportable) ?: false
}

fun ItemStack.getBehaviour(): ItemBehaviour {
    return ItemBehaviour.valueOf(this.getItemData<String>(ItemDataKey.Behaviour) ?: ItemBehaviour.GENERIC.name)
}

fun ItemStack.getCustomBlockId(): String? {
    return this.getItemData<String>(ItemDataKey.CustomBlockId)
}


inline fun <reified T> ItemStack.getItemData(key: ItemDataKey): T? {
    val itemMeta = this.itemMeta ?: return null
    return MetaManager.getItemData(itemMeta, key)
}