package me.mochibit.defcon

import com.tterrag.registrate.Registrate
import me.mochibit.defcon.foundation.async.ModDispatchers
import me.mochibit.defcon.foundation.err
import me.mochibit.defcon.foundation.registry.PreFreezeCommonRegistry
import me.mochibit.defcon.foundation.registry.autoRegister
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab

object DefconMod {
    const val MOD_ID = "defcon"
    private var initialized = false


    private val _registrate: Registrate =
        Registrate
            .create(MOD_ID)
            .defaultCreativeTab(null as ResourceKey<CreativeModeTab>?)


    val registrate: Registrate get() {
        if (!initialized) {
            throw IllegalStateException("Create registrate was not initialized!")
        }
        return _registrate
    }

    fun commonPreFreezeSetup(registry: Registry<*>) {
        autoRegister<PreFreezeCommonRegistry>(registry)
    }

    fun commonSetup(registrateConfiguration: Registrate.() -> Unit) {
        if (initialized) {
            return "Common was already initialized".err()
        }
        initialized = true
        _registrate.registrateConfiguration()
        ModDispatchers.setupEvents()
//        autoRegister<CommonRegistry>()
//        autoHandler<CommonGuiEventHandler>()
//        autoHandler<CommonEventHandler>()
    }
}

val ModRegistrate: Registrate = DefconMod.registrate