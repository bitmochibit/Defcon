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

package me.mochibit.defcon.listeners.blocks

import me.mochibit.defcon.radiation.RadiationArea
import me.mochibit.defcon.radiation.RadiationAreaFactory
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

class RadiationAreaExpand: Listener {

    // On block update, if the block has a neighbor wich is a radioactive block, expand the radiation area

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        // Check if the neighbor block has a radiation level
        // If it has, expand the radiation area
        return;

        // Loop every direction and get the adjacent block
        for (direction in BlockFace.entries) {
            val adjacentBlock = event.block.getRelative(direction)
            // Check if the block is a radioactive block

            val areas = RadiationArea.getAtLocation(adjacentBlock.location);
            if (areas.isEmpty()) continue

            val firstArea = areas.first()
            // Expand the radiation area
            RadiationAreaFactory.fromCenter(adjacentBlock.location, )
        }
    }

}