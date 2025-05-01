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

package me.mochibit.defcon.radiation

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.events.customitems.GeigerDetectEvent
import me.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import me.mochibit.defcon.player.PlayerData
import me.mochibit.defcon.save.savedata.PlayerDataSave
import me.mochibit.defcon.threading.scheduling.intervalAsync
import org.bukkit.Bukkit
import org.bukkit.GameMode
import kotlin.time.Duration.Companion.seconds

object RadiationManager {
    fun start() {
        intervalAsync(1.seconds) {
            updatePlayers()
        }
    }


    private suspend fun updatePlayers() = withContext(Dispatchers.IO) {
        val players = Defcon.instance.server.onlinePlayers
        val playerDataSave = PlayerDataSave.getInstance()
        for (player in players) {
            val radiationAreas = RadiationArea.getAtLocation(player.location.add(0.0, 1.0, 0.0))
            if (radiationAreas.isEmpty()) continue

            val totalRadiation = radiationAreas.sumOf { it.radiationLevel } / radiationAreas.size
            if (totalRadiation == 0.0) continue

            val geigerDetectEvent = GeigerDetectEvent(player, totalRadiation)
            withContext(Defcon.instance.minecraftDispatcher) {
                Bukkit.getPluginManager().callEvent(geigerDetectEvent)
            }


            if (player.gameMode.let { it == GameMode.SURVIVAL || it == GameMode.ADVENTURE }) {
                if (totalRadiation < 3.0) continue
                val radSuffocateEvent = RadiationSuffocationEvent(player, totalRadiation, radiationAreas)
                withContext(Defcon.instance.minecraftDispatcher) rad@{
                    Bukkit.getPluginManager().callEvent(radSuffocateEvent)
                    if (radSuffocateEvent.isCancelled()) {
                        return@rad
                    }
                    player.damage(1.0 * (totalRadiation / radiationAreas.size))
                }
            }

            val playerData = playerDataSave.getPlayerData(player) ?: PlayerData(player, 0.0)
            playerData.radiationLevel += totalRadiation / 20 // Ticks

            playerDataSave.savePlayerData(player, playerData.radiationLevel)

            withContext(Defcon.instance.minecraftDispatcher) {
                playerData.radiationLevel.apply {
                    if (this <= 0) return@apply

                    val maxHealthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
                    maxHealthAttribute?.baseValue = 20.0 - this.coerceAtMost(20.0)

                    if (this > 5.0) {
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.GLOWING,
                                100,
                                1
                            )
                        )
                    }

                    if (this > 10.0) {
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.NAUSEA,
                                100,
                                1
                            )
                        )
                    }

                    if (this > 15.0) {
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS,
                                100,
                                1
                            )
                        )
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.MINING_FATIGUE,
                                100,
                                1
                            )
                        )
                    }


                }
            }
        }
    }
}