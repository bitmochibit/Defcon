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

package me.mochibit.defcon.registers

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger.info
import me.mochibit.defcon.Defcon.Logger.warn
import me.mochibit.defcon.classes.CustomItemDefinition
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.enums.ConfigurationStorage
import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.interfaces.PluginItem
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin

/**
 * This class handles the registration of the definitions items
 * All the registered items are stored and returned in a form of a HashMap(id, CustomItem)
 *
 * To initialize create an instance of ItemRegister and execute the method registerItems, it will automatically
 * load up correctly the definitions items
 *
 */
class ItemRegister {
    /**
     *
     * @return boolean - True if all items are registered, false if some error occurred.
     */
    fun registerItems(): Boolean {
        registeredItems = HashMap()
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        val itemConfig = PluginConfiguration.get(ConfigurationStorage.Items).config
        val configurationItems = itemConfig.getList("enabled-items")
        if (configurationItems == null || configurationItems.isEmpty()) {
            warn("No items enabled found in the items.yml file")
            return false
        }

        configurationItems.forEach { item ->
            val itemId = item.toString()
            registeredItems[itemId]?.let {
                warn("Item $itemId is already registered (probably duplicated?), skipping")
                return@forEach
            }

            val itemName = itemConfig.getString("$item.item-name") ?: run {
                warn("Could not register item $itemId, because the item-name is not set")
                return@forEach
            }

            val itemDescription = itemConfig.getString("$item.item-description")
            val itemMinecraftId = if (versionGreaterOrEqualThan("1.21.3")) {
                itemConfig.getString("$item.item-minecraft-id")
            } else {
                itemConfig.getString("$item.legacy-minecraft-id") ?: let {
                    warn("Legacy minecraft id for item $itemId is not set, trying to get the new one, it will default to the base one, but it can give problems")
                    itemConfig.getString("$item.item-minecraft-id")
                }
            } ?: run {
                warn("Could not register item $itemId, because the legacy-minecraft-id is not set")
                return@forEach
            }

            val itemCustomBlockId = itemConfig.getString("$item.definitions-block-id", null)

            val itemStackSize = itemConfig.getInt("$item.max-stack-size")
            val itemUsable = itemConfig.getBoolean("$item.is-usable")
            val itemTransportable = itemConfig.getBoolean("$item.is-transportable")
            val itemDroppable = itemConfig.getBoolean("$item.is-droppable")

            val itemEquipable = itemConfig.getBoolean("$item.is-equipable", false)

            val itemModel = itemConfig.getString("$item.item-model")?.let {
                NamespacedKey.fromString(it)
            }

            val itemModelId = itemConfig.getInt("$item.item-model-id").let {
                if (it <= 0)
                    null
                else
                    it
            }

            val equipSlot = EquipmentSlot.valueOf(itemConfig.getString("$item.equip-slot")?.uppercase() ?: "HAND")

            var behaviourName = itemConfig.getString("$item.behaviour")
            if (behaviourName == null) {
                behaviourName = "generic"
            }
            val behaviourValue = ItemBehaviour.fromString(behaviourName)
                ?: throw IllegalArgumentException("Behaviour $behaviourName is not valid")

            val customItem: PluginItem = CustomItemDefinition(
                id = itemId,
                name = itemName,
                description = itemDescription,
                minecraftId = itemMinecraftId,
                itemModel = itemModel,
                itemModelId = itemModelId,
                customBlockId = itemCustomBlockId,
                equipSlot = equipSlot,

                isEquipable = itemEquipable,
                isUsable = itemUsable,
                isTransportable = itemTransportable,
                stackSize = itemStackSize,
                isDroppable = itemDroppable,
                behaviour = behaviourValue
            )
            info("Registered item $itemId")
            registeredItems[customItem.id] = customItem
        }

        return true
    }

    companion object {
        /**
         * Static member to access the registered items
         */
        var registeredItems: HashMap<String?, PluginItem?> = HashMap()

    }
}
