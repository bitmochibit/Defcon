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

package me.mochibit.defcon

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import me.mochibit.defcon.Defcon.Logger.PluginLogger
import me.mochibit.defcon.Defcon.Logger.info
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.events.customitems.GeigerDetectEvent
import me.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import me.mochibit.defcon.extensions.getRadiationLevel
import me.mochibit.defcon.extensions.increaseRadiationLevel
import me.mochibit.defcon.notification.NotificationManager
import me.mochibit.defcon.radiation.RadiationArea
import me.mochibit.defcon.registers.*
import me.mochibit.defcon.server.ResourcePackServer
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.plugin.java.JavaPlugin


class Defcon : JavaPlugin() {
    override fun onLoad() {
        _instance = this
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().load()

        EventRegister.registerPacketListeners()
        info("Defcon is starting up ☢️")
    }

    override fun onEnable() {
        ResourcePackServer.startServer()
        PacketEvents.getAPI().init()
        info("Plugin is enabled! Configuring...")
        PluginConfiguration.initializeAll()

        info("Registering resource pack and datapack")
        DatapackRegister.register()
        ResourcePackRegister.register()

        NotificationManager.startBroadcastTask()

        /* Register all plugin's listeners */
        EventRegister.registerListeners()

        /* Register definitions items */
        if (!ItemRegister().registerItems()) {
            info("Some items were not registered!")
        }

        /* Register definitions blocks */
        BlockRegister().registerBlocks()

        /* Register commands */
        CommandRegister().registerCommands()

        /* Register structures */
        StructureRegister().registerStructures()

        //TODO: Refactor
        Bukkit.getScheduler().runTaskTimer(
            this, Runnable {
                // Loop through every player and check if they are in a radiation area
                for (player in Bukkit.getOnlinePlayers()) {
                    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue

                    val radiationAreas = RadiationArea.getAtLocation(player.location.add(0.0, 1.0, 0.0))
                    if (radiationAreas.isEmpty()) continue

                    val totalRadiation = radiationAreas.sumOf { it.radiationLevel } / radiationAreas.size
                    if (totalRadiation == 0.0) continue

                    val geigerDetectEvent = GeigerDetectEvent(player, totalRadiation)
                    Bukkit.getPluginManager().callEvent(geigerDetectEvent)

                    for (radiationArea in radiationAreas) {
                        if (radiationArea.radiationLevel < 3.0) continue
                        val radSuffocateEvent = RadiationSuffocationEvent(player, radiationArea)
                        Bukkit.getPluginManager().callEvent(radSuffocateEvent)
                        if (radSuffocateEvent.isCancelled()) {
                            continue
                        }
                        player.damage(1.0)
                    }
                    player.increaseRadiationLevel(totalRadiation / 20)
                }
            },
            0, 20
        )

        //TODO: Refactor
        Bukkit.getScheduler().runTaskTimer(
            this, Runnable {
                for (player in Bukkit.getOnlinePlayers()) {
                    val radLevel = player.getRadiationLevel()
                    if (radLevel <= 0.0) continue

                    // Decrease max health
                    player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue =
                        20.0 - radLevel.coerceAtMost(20.0)

                    if (radLevel > 5.0) {
                        // Give nausea effect
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.GLOWING,
                                100,
                                1
                            )
                        )
                    }
                    if (radLevel > 10.0) {
                        // Give poison effect
                        player.addPotionEffect(
                            org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.NAUSEA,
                                100,
                                1
                            )
                        )
                    }

                    if (radLevel > 15.0) {
                        // Give poison effect
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
            },
            0, 20
        )

    }

    override fun onDisable() {
        PluginConfiguration.saveAll()
        NotificationManager.saveNotifications()
        PacketEvents.getAPI().terminate()
        ResourcePackServer.stopServer()
        info("Plugin disabled!")
    }

    companion object {
        private lateinit var _instance: Defcon
        val instance get() = _instance
        var namespace = "defcon"
        val minecraftVersion = Bukkit.getServer().bukkitVersion.split("-")[0]

    }

    object Logger {
        fun interface PluginLogger {
            fun log(message: String)
        }

        private fun PluginLogger.withPrefix(level: LogLevel) = PluginLogger {
            val prefix = level.getPrefix()
            log("$prefix $it")
        }

        enum class LogLevel {
            INFO,
            WARNING,
            ERROR,
            DEBUG;

            fun getPrefix(): String {
                return when (this) {
                    INFO -> "<blue><b>INFO</b></blue> "
                    WARNING -> "<yellow><b>WARN</b></yellow> "
                    ERROR -> "<red><b>ERROR</b></red> "
                    DEBUG -> "<light_purple><b>DEBUG</b></light_purple> "
                }
            }
        }

        private val miniMessage = MiniMessage.miniMessage()

        private val pluginLogger = PluginLogger {
            instance.componentLogger.info(miniMessage.deserialize(it))
        }

        fun info(message: String) {
            pluginLogger.withPrefix(LogLevel.INFO).log(message)
        }

        fun warn(message: String) {
            pluginLogger.withPrefix(LogLevel.WARNING).log(message)
        }

        fun err(message: String) {
            pluginLogger.withPrefix(LogLevel.ERROR).log(message)
        }

        fun debug(message: String) {
            pluginLogger.withPrefix(LogLevel.DEBUG).log(message)
        }
    }
}
