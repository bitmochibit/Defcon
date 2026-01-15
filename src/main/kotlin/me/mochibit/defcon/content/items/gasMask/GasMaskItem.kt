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

package me.mochibit.defcon.content.items.gasMask

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemProperties
import me.mochibit.defcon.extensions.random
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot

data class GasMaskItem(
    override val properties: PluginItemProperties,
    override val unparsedBehaviourData: Map<String, Any>,
) : PluginItem<Nothing?>(properties, unparsedBehaviourData) {
    fun canProtectFromRadiation(radiationLevel: Double): Boolean {
        return true
        // TODO : To add gas mask protection data
    }

    override fun onEquip(
        player: Player,
        affectedSlot: EquipmentSlot,
    ) {
        if (affectedSlot != EquipmentSlot.HEAD) return
        playGasMaskEquipSound(player)
    }

    private fun playGasMaskEquipSound(player: Player) {
        Defcon.launch(Defcon.minecraftDispatcher) {
            val randomizedPitch = (0.6f..0.9f).random()
            player.world.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 2.0f, randomizedPitch)
        }
    }
}
