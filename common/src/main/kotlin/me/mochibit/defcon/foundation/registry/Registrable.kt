package me.mochibit.defcon.foundation.registry

import me.mochibit.defcon.foundation.services.PlatformService
import me.mochibit.defcon.foundation.services.platformService
import net.minecraft.core.Registry

interface Registrable {
    /**
     * The registration order priority. Lower values are registered first.
     * Override this property to control registration order relative to other AutoRegistrable implementations.
     * Default is 0, increase the value for registrations that depend on others (e.g., Ponders = 5).
     */
    val registrationOrder: Int
        get() = 0

    /**
     * If set, this registrable will only be executed on the specified environment.
     * Null means "any environment" (default).
     */
    val targetEnvironment: PlatformService.Environment?
        get() = null
    fun register(registry: Registry<*>? = null)
}

sealed interface PreFreezeCommonRegistry : Registrable

sealed interface CommonRegistry : Registrable

/**
 * Generic auto-registration function utility that uses sealed classes to automatically register, and discover, Registrable implementations.
 *
 * Lower [Registrable.registrationOrder] values are registered first.
 *
 * Pass a sealed interface that extends [Registrable] as the type parameter to automatically register all of its object implementations.
 *
 * Place the marker in the same package as the implementations!!
 *
 * It is platform-agnostic
 */
inline fun <reified AutoRegistrableMarker : Registrable> autoRegister(registry: Registry<*>? = null) {
    if (!AutoRegistrableMarker::class.isSealed) {
        throw IllegalArgumentException("The passed auto registrable marker must be a sealed interface")
    }

    val platform = platformService

    AutoRegistrableMarker::class
        .sealedSubclasses
        .filter { AutoRegistrableMarker::class.java.isAssignableFrom(it.java) }
        .map { it.objectInstance as Registrable }
        .filter { registrable ->
            val envMatch =
                registrable.targetEnvironment == null ||
                        registrable.targetEnvironment == platform.environment
            envMatch
        }.sortedBy { it.registrationOrder }
        .forEach { it.register(registry) }
}