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
