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

package me.mochibit.defcon.listeners.player

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.particles.emitter.ParticleEmitter
import me.mochibit.defcon.save.savedata.PlayerDataSave
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class PlayerDeathReset : Listener {
    private val playerDataSave = PlayerDataSave.getInstance()

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent){
        Defcon.instance.launch(Defcon.instance.asyncDispatcher) {
            // Get the player's UUID
            val player = event.player

            ParticleEmitter

            // Reset the player's data
            playerDataSave.savePlayerData(player, 0.0)

            if (versionGreaterOrEqualThan("1.21.3")) {
                player.getAttribute(MAX_HEALTH)
            } else {
                player.getAttribute(Registry.ATTRIBUTE.getOrThrow(NamespacedKey.minecraft("generic.max_health")))
            }?.let {
                it.baseValue = 20.0
            }
        }
    }
}