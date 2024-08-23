package com.mochibit.defcon.particles

import com.mochibit.defcon.lifecycle.Lifecycled
import com.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

class ParticleEmitter(val origin: Location, val range: Double) : Lifecycled {
    companion object {
        private const val MAX_PARTICLES = 5000
    }

    private val particles = mutableListOf<ParticleInstance>()

    fun spawnParticle(particle: AbstractParticle, location: Location) {
        if (particles.size >= MAX_PARTICLES) return
        particles.add(ParticleInstance.fromTemplate(particle, location))
    }

    override fun start() {
        // No initialization required
    }

    override fun update(delta: Float) {
        val players = getPlayersInRange()

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.update(delta, players)
            particle.show(players)

            if (particle.isDead()) {
                particle.remove(players)
                iterator.remove()
            }
        }
    }

    override fun stop() {
        val players = getPlayersInRange()
        particles.forEach { it.remove(players) }
        particles.clear()
    }

    private fun getPlayersInRange(): List<Player> {
        val world = origin.world
        return Bukkit.getOnlinePlayers().filter {
            it.world == world && it.location.distanceSquared(origin) <= range * range
        }
    }
}
