package me.mochibit.defcon.content.explosion.client

import me.mochibit.defcon.content.explosion.effects.ParticleVisualEffect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClientVisualEffects {
    private val activeEffects = ConcurrentHashMap<UUID, ParticleVisualEffect<*>>()

    fun register(id: UUID, effect: ParticleVisualEffect<*>) {
        activeEffects[id] = effect
    }

    fun stop(id: UUID) {
        activeEffects.remove(id)?.stop()
    }

    fun stopAll() {
        activeEffects.keys.toList().forEach { stop(it) }
    }

    fun unregister(id: UUID) {
        activeEffects.remove(id)
    }
}