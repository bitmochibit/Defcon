package me.mochibit.defcon

import com.tterrag.registrate.Registrate
import io.github.classgraph.ClassGraph
import me.mochibit.defcon.foundation.async.ModDispatchers
import me.mochibit.defcon.foundation.err
import me.mochibit.defcon.foundation.info
import me.mochibit.defcon.foundation.registry.CommonRegistry
import me.mochibit.defcon.foundation.registry.PreFreezeCommonRegistry
import me.mochibit.defcon.foundation.registry.Registrable
import me.mochibit.defcon.foundation.registry.autoRegister
import me.mochibit.defcon.foundation.services.platformService
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
        autoRegister<PreFreezeCommonRegistry>(registry)
    }

    fun commonSetup(registrateConfiguration: Registrate.() -> Unit = {}) {
        if (initialized) {
            return "Common was already initialized".err()
        }


        _registrate = Registrate.create(MOD_ID)
            .defaultCreativeTab(null as ResourceKey<CreativeModeTab>?)
        initialized = true

        _registrate.registrateConfiguration()
        ModDispatchers.setupEvents()
        autoRegister<CommonRegistry>()

        val rootPaths = platformService.getModRootPaths()
        ClassGraph()
            .enableAllInfo()
            .acceptPackages("me.mochibit.defcon.foundation.registry")
            .overrideClasspath(*rootPaths.toTypedArray())
            .scan()
            .use { scanResult ->
                scanResult.getClassesImplementing(CommonRegistry::class.java.name)
                    .mapNotNull { classInfo ->
                        try {
                            val clazz = Class.forName(
                                classInfo.name,
                                true,
                                DefconMod::class.java.classLoader
                            )
                            clazz.kotlin.objectInstance as? CommonRegistry
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .sortedBy { it.registrationOrder }
                    .filter { it.targetEnvironment == null || it.targetEnvironment == platformService.environment }
                    .forEach { it.javaClass.name.info() }
            }
    }
}

val ModRegistrate: Registrate get() = DefconMod.registrate