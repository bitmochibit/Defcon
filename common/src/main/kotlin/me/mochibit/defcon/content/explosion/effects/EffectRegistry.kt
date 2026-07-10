package me.mochibit.defcon.content.explosion.effects

import kotlinx.coroutines.CoroutineScope
import me.mochibit.defcon.foundation.particles.ParticleSpawner
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

typealias EffectFactory = (EffectParams, ParticleSpawner, CoroutineScope) -> ParticleVisualEffect<*>

@AutoRegister
object EffectRegistry {
    private val factories = ConcurrentHashMap<KClass<out EffectParams>, EffectFactory>()

    init {
        register<NuclearExplosionParams> {params, spawner, scope ->
            NuclearExplosionEffect(params as NuclearExplosionParams, spawner, scope)
        }
    }

    private inline fun <reified P : EffectParams> register(
        noinline factory: EffectFactory
    ) {
        val kClass = P::class
        if (factories.containsKey(kClass)) throw IllegalStateException("$kClass was already registered!")
        @Suppress("UNCHECKED_CAST")
        factories[kClass] = factory
    }

    fun build(params: EffectParams, spawner: ParticleSpawner, scope: CoroutineScope): ParticleVisualEffect<*> {
        val factory = factories[params::class]
            ?: error("No effect registered for params type ${params::class}")
        return factory(params, spawner, scope)
    }
}