package com.mochibit.defcon.listeners.items

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.ItemBehaviour
import com.mochibit.defcon.events.customitems.GeigerDetectEvent
import com.mochibit.defcon.extensions.getBehaviour
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GeigerCounterListener : Listener {
    @EventHandler
    fun onGeigerDetect(event: GeigerDetectEvent) {
        val player = event.player as org.bukkit.entity.Player
        val location = player.location

        if (event.radiationLevel <= 0) return;
        // Check if the item on the player's hand is a Geiger Counter
        if (player.inventory.itemInMainHand.getBehaviour() != ItemBehaviour.GEIGER_COUNTER &&
            player.inventory.itemInOffHand.getBehaviour() != ItemBehaviour.GEIGER_COUNTER) return;

        info(event.radiationLevel.toString())

        // Player a ticking sound randomly, to simulate the Geiger Counter, and with a density based on the radiation level, repeating a random number of times
        val ticks = (1.. event.radiationLevel.toInt() * 6).random()
        for (i in 0 until ticks) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
                player.playSound(location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
            }, (1..20).random().toLong())
        }
    }
}
