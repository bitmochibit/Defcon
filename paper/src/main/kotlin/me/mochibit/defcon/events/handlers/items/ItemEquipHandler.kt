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

package me.mochibit.defcon.events.handlers.items

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import me.mochibit.defcon.events.AutoRegisterHandler
import me.mochibit.defcon.extensions.getPluginItem
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

@AutoRegisterHandler(fromVersion = "1.21.4")
class ItemEquipHandler: Listener {
    @EventHandler
    fun onItemEquip(event: EntityEquipmentChangedEvent) {
        val player = event.entity as? Player ?: return
        for ((slot, equipmentChange) in event.equipmentChanges) {
            slot
            val newItem = equipmentChange.newItem()
            val pluginItem = newItem.getPluginItem() ?: continue
            pluginItem.onEquip(player, slot)
        }

        for ((slot, equipmentChange) in event.equipmentChanges) {
            val oldItem = equipmentChange.oldItem()
            val pluginItem = oldItem.getPluginItem() ?: continue
            pluginItem.onUnequip(player, slot)
        }
    }
}