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

package me.mochibit.defcon.interfaces

import me.mochibit.defcon.enums.ItemBehaviour
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.EquipmentSlot
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
    val itemModel: NamespacedKey?
    val equipSlot: EquipmentSlot
    val customBlockId: String?
    val isUsable: Boolean
    val isEquipable: Boolean
    val isDroppable: Boolean
    val stackSize: Int
    val isTransportable: Boolean

    /*Behaviour type*/
    val behaviour: ItemBehaviour
}
