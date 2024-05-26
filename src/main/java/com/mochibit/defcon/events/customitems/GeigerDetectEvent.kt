package com.mochibit.defcon.events.customitems

import com.mochibit.defcon.radiation.RadiationArea
import org.bukkit.entity.HumanEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GeigerDetectEvent(val player: HumanEntity, val radiationLevel: Double) : Event() {
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
}