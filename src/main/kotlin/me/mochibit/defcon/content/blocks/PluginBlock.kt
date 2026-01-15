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

package me.mochibit.defcon.content.blocks

import com.jeff_media.customblockdata.CustomBlockData
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.content.element.Element
import me.mochibit.defcon.content.element.ElementBehaviourPropParser
import me.mochibit.defcon.content.element.ElementBehaviourProperties
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.extensions.PluginBlockPropertyKeys
import me.mochibit.defcon.extensions.removeData
import me.mochibit.defcon.extensions.setData
import me.mochibit.defcon.registry.ItemRegistry
import org.bukkit.World

/**
 * Abstract base class for all plugin blocks.
 *
 * @param B The behaviour properties type for this block (use Nothing? for blocks without special behavior)
 */
abstract class PluginBlock<out B : ElementBehaviourProperties?>(
    open override val properties: PluginBlockProperties,
    open override val unparsedBehaviourData: Map<String, Any>,
    final override val behaviourPropParser: ElementBehaviourPropParser? = null,
) : Element<PluginBlockProperties, B> {
    /**
     * Lazily computed behavior properties. Override in subclasses that need typed access.
     */
    @Suppress("UNCHECKED_CAST")
    override val behaviourProperties: B
        get() = behaviourPropParser?.parse(unparsedBehaviourData) as B

    /**
     * The linked item for this block.
     */
    val linkedItem: PluginItem<*>?
        get() = ItemRegistry.getItem(properties.id)

    /**
     * Places this block at the specified location.
     */
    fun placeBlock(
        x: Double,
        y: Double,
        z: Double,
        world: World,
    ) {
        val block = world.getBlockAt(x.toInt(), y.toInt(), z.toInt())
        val blockData = CustomBlockData(block, Defcon)
        blockData.setData(PluginBlockPropertyKeys.blockId, properties.id)
    }

    /**
     * Removes this block from the specified location.
     */
    fun removeBlock(
        x: Double,
        y: Double,
        z: Double,
        world: World,
    ) {
        val block = world.getBlockAt(x.toInt(), y.toInt(), z.toInt())
        val blockData = CustomBlockData(block, Defcon)

        blockData.removeData(PluginBlockPropertyKeys.blockId)
    }
}
