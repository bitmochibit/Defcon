package me.mochibit.defcon.foundation.registry

import me.mochibit.defcon.foundation.services.PlatformService
import me.mochibit.defcon.foundation.services.classScanningService
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

enum class RegistryPhase {
    PRE_FREEZE,
    COMMON
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRegister(val phase: RegistryPhase = RegistryPhase.COMMON)


object AutoRegistrar {

    fun registerAll(phase: RegistryPhase, registry: Registry<*>? = null) {
        val platform = platformService

        classScanningService.getClassesAnnotatedByWithData(AutoRegister::class.java)
            .filter { (_, data) -> phaseOf(data) == phase }
            .mapNotNull { (clazz, _) -> resolveInstance(clazz) as? Registrable }
            .filter { it.targetEnvironment == null || it.targetEnvironment == platform.environment }
            .sortedBy { it.registrationOrder }
            .forEach { it.register(registry) }
    }

    private fun phaseOf(annotationData: Map<String, Any>): RegistryPhase {
        val raw = annotationData["phase"] as? Array<*> ?: return RegistryPhase.COMMON
        val name = raw.getOrNull(1) as? String ?: return RegistryPhase.COMMON
        return RegistryPhase.valueOf(name)
    }

    private fun resolveInstance(clazz: Class<*>): Any? =
        runCatching { clazz.getField("INSTANCE").get(null) }
            .getOrElse {
                runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
            }
}