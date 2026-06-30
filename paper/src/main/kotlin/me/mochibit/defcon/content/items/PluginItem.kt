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

package me.mochibit.defcon.content.items

import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.element.Element
import me.mochibit.defcon.content.element.ElementBehaviourPropParser
import me.mochibit.defcon.content.element.ElementBehaviourProperties
import me.mochibit.defcon.registry.BlockRegistry
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * Abstract base class for all plugin items.
 *
 * @param B The behaviour properties type for this item (use Nothing? for items without special behavior)
 */
abstract class PluginItem<out B : ElementBehaviourProperties?>(
    open override val properties: PluginItemProperties,
    open override val unparsedBehaviourData: Map<String, Any>,
    final override val behaviourPropParser: ElementBehaviourPropParser? = null,
) : Element<PluginItemProperties, B> {
    /**
     * Lazily computed behavior properties. Override in subclasses that need typed access.
     */
    @Suppress("UNCHECKED_CAST")
    override val behaviourProperties: B
        get() = behaviourPropParser?.parse(unparsedBehaviourData) as B

    /**
     * The display name of this item with all formatting stripped.
     */
    val name: String by lazy {
        miniMessage.stripTags(properties.displayName)
    }

    /**
     * Creates a new ItemStack instance from this immutable template.
     * This is the only mutable game object that should exist.
     */
    val itemStack: ItemStack
        get() = itemStackFactory.create(properties)

    /**
     * Whether this item can be equipped in an armor slot.
     */
    val isEquippable: Boolean
        get() = properties.equipmentSlot != null

    /**
     * Whether this item is specifically armor (excludes offhand and mainhand).
     */
    val isArmor: Boolean
        get() = properties.equipmentSlot?.isArmor ?: false

    /**
     * The linked block for this item, if it's a block item.
     */
    val linkedBlock: PluginBlock<*>?
        get() = BlockRegistry.getBlock(properties.id)

    /**
     * Called when a player equips this item.
     */
    open fun onEquip(
        player: Player,
        affectedSlot: EquipmentSlot,
    ) {}

    /**
     * Called when a player unequips this item.
     */
    open fun onUnequip(
        player: Player,
        affectedSlot: EquipmentSlot,
    ) {}

    companion object {
        private val miniMessage = MiniMessage.miniMessage()
        private val itemStackFactory = FactoryMetaStrategies.getFactory()
    }
}
