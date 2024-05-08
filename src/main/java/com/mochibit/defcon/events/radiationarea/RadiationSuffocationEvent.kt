package com.mochibit.defcon.events.radiationarea

import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.radiation.RadiationAreaFactory
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RadiationSuffocationEvent(private val damagedPlayer: Player, private val fromArea: RadiationArea) : Event() {
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

    fun getRadiationArea(): RadiationArea {
        return fromArea
    }

}