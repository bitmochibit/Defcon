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

package me.mochibit.defcon.classes

import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.enums.ItemDataKey
import me.mochibit.defcon.interfaces.PluginItem
import me.mochibit.defcon.utils.ColorUtils
import me.mochibit.defcon.utils.MetaManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.components.EquippableComponent

/**
 * This class defines a definitions item
 * It provides a way to generate effective minecraft items from this definition
 */
@Suppress("UnstableApiUsage")
class CustomItemDefinition(
    override val id: String,
    name: String,
    override val description: String?,
    override val minecraftId: String,
    override val itemModel: NamespacedKey?,
    override val equipSlot: EquipmentSlot,
    override val customBlockId: String?,
    override val isUsable: Boolean,
    override val isEquipable: Boolean,
    override val isDroppable: Boolean,
    override val stackSize: Int,
    override val isTransportable: Boolean,
    override val behaviour: ItemBehaviour,
) : PluginItem {

    override val name : String = name
        get() = ColorUtils.stripColor(field)

    override val displayName: String
        get() = ColorUtils.parseColor(name)


    override val itemStack: ItemStack
        get() {
            val material = Material.getMaterial(minecraftId) ?: throw IllegalArgumentException("Material $minecraftId does not exist")
            val customItem = ItemStack(material)

            /* Meta assignment */
            val itemMeta = customItem.itemMeta

            itemMeta.displayName(MiniMessage.miniMessage().deserialize(displayName))

            if (description != null) {
                itemMeta.lore(
                    description.split("\n").map { MiniMessage.miniMessage().deserialize(it) }
                )
            }

            MetaManager.setItemData(itemMeta, ItemDataKey.ItemID, id)
            MetaManager.setItemData(itemMeta, ItemDataKey.StackSize, stackSize)
            MetaManager.setItemData(itemMeta, ItemDataKey.Usable, isUsable)
            MetaManager.setItemData(itemMeta, ItemDataKey.Equipable, isEquipable)
            MetaManager.setItemData(itemMeta, ItemDataKey.Droppable, isDroppable)
            MetaManager.setItemData(itemMeta, ItemDataKey.Transportable, isTransportable)
            MetaManager.setItemData(itemMeta, ItemDataKey.Behaviour, behaviour.name)

            if (customBlockId != null) {
                MetaManager.setItemData(itemMeta, ItemDataKey.CustomBlockId, customBlockId)
            }

            itemModel?.let {
                itemMeta.itemModel = it
            }

            println(equipSlot)

            val component = itemMeta.equippable
            component.slot = equipSlot

            itemMeta.setEquippable(component)

            customItem.setItemMeta(itemMeta)

            return customItem
        }
}
