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

package me.mochibit.defcon.content.items.radiationMeasurer

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.delay
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemProperties
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

data class RadiationMeasurerItem(
    override val properties: PluginItemProperties,
    override val unparsedBehaviourData: Map<String, Any>,
) : PluginItem<Nothing?>(properties, unparsedBehaviourData) {
    companion object {
        // Track last sound play time for each player to prevent spam
        private val lastSoundTime = ConcurrentHashMap<Player, Long>()
        private const val SOUND_COOLDOWN_MS = 500L // 500ms cooldown between sound effects
    }

    fun playClickingSound(
        location: Location,
        radiationLevel: Double,
    ) {
        val player = location.world.players.firstOrNull { it.location.distance(location) < 1.0 } ?: return
        val currentTime = System.currentTimeMillis()
        val lastTime = lastSoundTime[player] ?: 0L

        // Check cooldown
        if (currentTime - lastTime < SOUND_COOLDOWN_MS) return

        lastSoundTime[player] = currentTime

        Defcon.launch(Defcon.minecraftDispatcher) {
            val clickCount = (radiationLevel / 4.0).coerceIn(1.0, 5.0).toInt()
            val baseDelay = (100L / radiationLevel).coerceIn(50.0, 200.0)

            repeat(clickCount) {
                location.world.playSound(location, Sound.UI_BUTTON_CLICK, 0.3f, 1.5f + (radiationLevel / 20.0).toFloat())
                delay(baseDelay.toLong() + (0L..20L).random())
            }
        }
    }

    fun showMeasurementTitle(
        player: Player,
        radiationLevel: Double,
    ) {
        val percentage = (radiationLevel * 100).toInt().coerceIn(0, 100)

        // Create radiation bar with gradient effect
        val barLength = 20
        val filledBars = (percentage / 5).coerceIn(0, barLength)

        val bar =
            buildString {
                for (i in 0 until barLength) {
                    when {
                        i < filledBars -> {
                            when {
                                percentage < 30 -> append("█")

                                // Low - solid green
                                percentage < 60 -> append("▓")

                                // Medium - checkered yellow
                                percentage < 80 -> append("▒")

                                // High - sparse orange
                                else -> append("░") // Critical - dotted red
                            }
                        }

                        else -> {
                            append("│")
                        }
                    }
                }
            }

        // Color based on radiation level
        val color =
            when {
                percentage < 30 -> NamedTextColor.GREEN
                percentage < 60 -> NamedTextColor.YELLOW
                percentage < 80 -> NamedTextColor.GOLD
                else -> NamedTextColor.RED
            }

        val statusText =
            when {
                percentage < 30 -> Component.text("SAFE", NamedTextColor.GREEN, TextDecoration.BOLD)
                percentage < 60 -> Component.text("CAUTION", NamedTextColor.YELLOW, TextDecoration.BOLD)
                percentage < 80 -> Component.text("DANGER", NamedTextColor.GOLD, TextDecoration.BOLD)
                else -> Component.text("☢ CRITICAL ☢", NamedTextColor.RED, TextDecoration.BOLD)
            }

        val message =
            Component
                .text()
                .append(Component.text("☢ RADIATION ☢ ", color, TextDecoration.BOLD))
                .append(Component.text("[", color))
                .append(Component.text(bar, color))
                .append(Component.text("] ", color))
                .append(Component.text("$percentage% ", NamedTextColor.WHITE))
                .append(statusText)
                .build()

        player.sendActionBar(message)
    }
}
