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

package com.mochibit.defcon.customassets.items

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.*

data class ModelData(
    val originalItem : Material = Material.FLINT,
    val originalItemName: String = originalItem.name.lowercase(Locale.getDefault()),
    val modelName: String,
    val parent: ParentType = ParentType.ITEM_GENERATED,
    val textures: Map<String, String> = mapOf("layer0" to "${if (originalItem.isBlock) "block" else "item"}/${originalItemName}"),
    val customModelData: Int = 1,
    val model: String = "${if (originalItem.isBlock) "block" else "item"}/$modelName/$modelName"
    )
