/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

import com.jeff_media.customblockdata.CustomBlockData
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.registry.BlockRegistry
import me.mochibit.defcon.registry.ItemRegistry
import org.bukkit.block.Block

fun Block.getPluginBlock(): PluginBlock<*>? {
    val customBlockData = CustomBlockData(this, Defcon)
    val blockId = customBlockData.getData(PluginBlockPropertyKeys.blockId) ?: return null
    return BlockRegistry.getBlock(blockId)
}
