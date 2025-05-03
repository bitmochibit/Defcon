/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.extensions

import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.enums.ItemDataKey
import me.mochibit.defcon.utils.MetaManager
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

fun ItemStack.equipSlotNumber(): Int {
    return this.getItemData<Int>(ItemDataKey.EquipSlotNumber) ?: 0
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