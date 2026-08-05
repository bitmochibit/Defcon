package me.mochibit.defcon.foundation.registry

import me.mochibit.defcon.commands.CommandEntry
import me.mochibit.defcon.foundation.eventbus.CommonEvents
import me.mochibit.defcon.foundation.eventbus.EventBus
import net.minecraft.core.Registry

@AutoRegister
object ModCommands: Registrable {
    override fun register(registry: Registry<*>?) {
        EventBus.on<CommonEvents.RegisterCommandsEvent> { event ->
            CommandEntry.registerAll(event.dispatcher, event.env, event.context)
        }
    }
}