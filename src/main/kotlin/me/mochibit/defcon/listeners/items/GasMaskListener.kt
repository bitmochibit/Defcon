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

package me.mochibit.defcon.listeners.items

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import kotlinx.coroutines.delay
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.enums.ItemBehaviour
import me.mochibit.defcon.events.equip.CustomItemEquipEvent
import me.mochibit.defcon.events.radiationarea.RadiationSuffocationEvent
import me.mochibit.defcon.extensions.getBehaviour
import me.mochibit.defcon.extensions.random
import me.mochibit.defcon.registers.listener.UniversalVersionIndicator
import me.mochibit.defcon.registers.listener.VersionIndicator
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent


@UniversalVersionIndicator
class GasMaskProtectListener: Listener {
    @EventHandler
    fun protectFromGas(event: RadiationSuffocationEvent) {
        val player = event.getPlayer()

        // Check if the player has a gas mask
        val helmet = player.inventory.helmet ?: return

        val itemBehaviour = helmet.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return

        // Cancel the event
        event.setCancelled(true)
    }
}

@VersionIndicator("1.21.4")
class GasMaskEquipListener : Listener {
    @EventHandler
    fun onGasMaskEquip(event: EntityEquipmentChangedEvent) {
        val player = event.entity as? Player ?: return

        // Check if the player has equipped a gas mask
        val helmet = player.inventory.helmet ?: return

        val itemBehaviour = helmet.getBehaviour()
        if (itemBehaviour != ItemBehaviour.GAS_MASK) return

        // Play the sound
        playGasMaskSound(player)
    }

    private fun playGasMaskSound(player: Player) {
        Defcon.instance.launch(Defcon.instance.minecraftDispatcher) {
            val randomizedPitch = (0.6f..0.9f).random()
            player.world.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 2.0f, randomizedPitch)
        }
    }
}

@VersionIndicator("1.20", "1.21.3")
class GasMaskEquipListenerLegacy: Listener {

    @EventHandler
    fun onGasMaskEquip(event: CustomItemEquipEvent) {
        // Check if the item is a gas mask
        if (event.equippedItem.getBehaviour() != ItemBehaviour.GAS_MASK) return

        // Check if the player has no helmet (so right-click would equip it)
        val currentHelmet = event.player.inventory.helmet
        if (currentHelmet != null && !currentHelmet.type.isAir) return

        // The right-click auto-equip happens AFTER this event fires
        // So we need a delayed task to check if it was actually equipped
        Defcon.instance.launch {
            delay(1.ticks)
            val helmet = event.player.inventory.helmet
            if (helmet != null && helmet.getBehaviour() == ItemBehaviour.GAS_MASK) {
                playGasMaskSound(event.player)
            }
        }
    }

//    @EventHandler()
//    fun onGasMaskEquip(event: InventoryClickEvent) {
//        val player = event.whoClicked as? Player ?: return
//
//        // Case 1: Player clicks on helmet slot (slot 39)
//        if (event.slot == 39 || event.rawSlot == 39) {
//            // Check if player is putting on gas mask from cursor
//            val cursorItem = event.cursor
//            if (!cursorItem.type.isAir && cursorItem.getBehaviour() == ItemBehaviour.GAS_MASK) {
//                // Player is placing gas mask on head - schedule check after event processes
//                Defcon.instance.launch {
//                    delay(1.ticks)
//                    val helmet = player.inventory.helmet
//                    if (helmet != null && helmet.getBehaviour() == ItemBehaviour.GAS_MASK) {
//                        playGasMaskSound(player)
//                    }
//                }
//                return
//            }
//        }
//
//        // Case 2: Shift-clicking gas mask from inventory
//        if (event.isShiftClick) {
//            val clickedItem = event.currentItem ?: return
//            if (clickedItem.getBehaviour() == ItemBehaviour.GAS_MASK) {
//                // Check if helmet slot is empty (so shift-click would equip it)
//                if (player.inventory.helmet == null || player.inventory.helmet?.type?.isAir == true) {
//                    // Schedule task with slightly longer delay to ensure inventory updates first
//                    Defcon.instance.launch {
//                        delay(1.ticks)
//                        val helmet = player.inventory.helmet
//                        if (helmet != null && helmet.getBehaviour() == ItemBehaviour.GAS_MASK) {
//                            playGasMaskSound(player)
//                        }
//                    }
//                }
//            }
//        }
//
//        // Case 3: Number key hotswap (pressing 1-9 while hovering over helmet)
//        if (event.click == ClickType.NUMBER_KEY) {
//            val hotbarItem = player.inventory.getItem(event.hotbarButton)
//            if (hotbarItem != null && hotbarItem.getBehaviour() == ItemBehaviour.GAS_MASK &&
//                (event.slot == 39 || event.rawSlot == 39)
//            ) {
//                Defcon.instance.launch {
//                    delay(1.ticks)
//                    val helmet = player.inventory.helmet
//                    if (helmet != null && helmet.getBehaviour() == ItemBehaviour.GAS_MASK) {
//                        playGasMaskSound(player)
//                    }
//                }
//            }
//        }
//    }
//
//    @EventHandler
//    fun onGasMaskEquipFast(event: PlayerInteractEvent) {
//        // Only handle right-click air or right-click block events
//        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
//
//        val player = event.player
//        val item = event.item ?: return
//
//        // Check if item is gas mask
//        if (item.getBehaviour() != ItemBehaviour.GAS_MASK) return
//
//        // Check if player has no helmet (so right-click would equip it)
//        val currentHelmet = player.inventory.helmet
//        if (currentHelmet != null && !currentHelmet.type.isAir) return
//
//        // The right-click auto-equip happens AFTER this event fires
//        // So we need a delayed task to check if it was actually equipped
//        Defcon.instance.launch {
//            delay(1.ticks)
//            val helmet = player.inventory.helmet
//            if (helmet != null && helmet.getBehaviour() == ItemBehaviour.GAS_MASK) {
//                playGasMaskSound(player)
//            }
//        }
//    }
}

// Helper function to play the sound
private fun playGasMaskSound(player: Player) {
    Defcon.instance.launch(Defcon.instance.minecraftDispatcher) {
        val randomizedPitch = (0.6f..0.9f).random()
        player.world.playSound(player.location, Sound.ENTITY_PLAYER_BREATH, 2.0f, randomizedPitch)
    }
}
