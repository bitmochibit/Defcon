package me.mochibit.defcon

import com.tterrag.registrate.Registrate
import me.mochibit.defcon.content.explosion.processor.PostApocalypticTerrain
import me.mochibit.defcon.foundation.async.ModDispatchers
import me.mochibit.defcon.foundation.err
import me.mochibit.defcon.foundation.registry.AutoRegistrar
import me.mochibit.defcon.foundation.registry.RegistryPhase
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab

object DefconMod {
    const val MOD_ID = "defcon"

    private lateinit var _registrate: Registrate
    private var initialized = false

    val registrate: Registrate
        get() {
            check(initialized) { "Registrate accessed before DefconMod.commonSetup() was called" }
            return _registrate
        }

    fun commonPreFreezeSetup(registry: Registry<*>) {
        AutoRegistrar.registerAll(RegistryPhase.PRE_FREEZE, registry)
    }

    fun commonSetup(registrateConfiguration: Registrate.() -> Unit = {}) {
        if (initialized) {
            return "Common was already initialized".err()
        }

        _registrate = Registrate.create(MOD_ID)
            .defaultCreativeTab(null as ResourceKey<CreativeModeTab>?)
        initialized = true

        _registrate.registrateConfiguration()

        PostApocalypticTerrain.setupEvents() // TODO: auto register
        ModDispatchers.setupEvents()


        AutoRegistrar.registerAll(RegistryPhase.COMMON)
    }
}

val ModRegistrate: Registrate get() = DefconMod.registrate