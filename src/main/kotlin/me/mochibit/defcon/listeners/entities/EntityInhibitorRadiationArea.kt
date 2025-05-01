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

package me.mochibit.defcon.listeners.entities

import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.biomes.definitions.BurningAirBiome
import me.mochibit.defcon.biomes.definitions.NuclearFalloutBiome
import me.mochibit.defcon.extensions.toTicks
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntitySpawnEvent
import java.net.http.WebSocket.Listener
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class EntityInhibitorRadiationArea: Listener {

    @EventHandler
    fun onEntitySpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        val location = entity.location

        val biomeAtLoc = CustomBiomeHandler.getBiomeAtLocation(location) ?: return

        when (biomeAtLoc.biome) {
            NuclearFalloutBiome.asBukkitBiome.key -> {
                event.isCancelled = true
                entity.remove()
            }
            BurningAirBiome.asBukkitBiome.key -> {
                entity.fireTicks = 2.minutes.toTicks().toInt()
            }
        }
    }
}