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

package me.mochibit.defcon.events.radiationarea

import me.mochibit.defcon.radiation.RadiationArea
import me.mochibit.defcon.radiation.RadiationAreaFactory
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RadiationSuffocationEvent(private val damagedPlayer: Player, private val totalRadLevel: Double, private val fromAreas: Collection<RadiationArea>) : Event() {
    private var isCancelled = false

    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    fun isCancelled(): Boolean {
        return isCancelled
    }

    fun getPlayer(): Player {
        return damagedPlayer
    }

}