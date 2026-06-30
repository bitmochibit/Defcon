/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2025 mochibit.
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

import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import me.mochibit.defcon.content.items.radiationMeasurer.RadiationMeasurerItem
import me.mochibit.defcon.events.AutoRegisterHandler
import me.mochibit.defcon.events.plugin.geiger.GeigerDetectEvent
import me.mochibit.defcon.extensions.getPluginItem
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.meta.CompassMeta

@AutoRegisterHandler
class RadiationMeasurerListener : Listener {
    @EventHandler
    fun onGeigerDetect(event: GeigerDetectEvent) {
        val player = event.player as Player

        if (event.radiationLevel <= 0) return

        val handItem = player.inventory.itemInMainHand
        val offHandItem = player.inventory.itemInOffHand

        val mainHandRadiationMeasurer = handItem.getPluginItem() as? RadiationMeasurerItem
        val offHandRadiationMeasurer = offHandItem.getPluginItem() as? RadiationMeasurerItem


        val firstNotNull = mainHandRadiationMeasurer ?: offHandRadiationMeasurer ?: return
        firstNotNull.playClickingSound(player.location, event.radiationLevel)
        firstNotNull.showMeasurementTitle(player, event.radiationLevel)
    }
}
