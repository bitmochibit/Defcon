package com.mochibit.defcon.events.blocks

import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class RadiationCheck : Listener {
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // Get the block at head level and if the radiation level is >= 1.0, damage the player by 1.0 health every second

        val player = event.player
        if (RadiationArea.checkIfInBounds(player.location)) {
            player.damage(1.0)
            return;
        }

        val block = player.location.add(0.0, 1.0, 0.0).block
        val radiationLevel = MetaManager.getBlockData<Double>(block.location, BlockDataKey.RadiationLevel)

        if (radiationLevel != null && radiationLevel >= 1.0) {
            player.damage(1.0)
        }
    }
}