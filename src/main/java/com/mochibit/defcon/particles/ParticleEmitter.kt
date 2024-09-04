package com.mochibit.defcon.particles

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.lifecycle.Lifecycled
import com.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3d

class ParticleEmitter(val origin: Location, val range: Double) : Lifecycled {
    companion object {
        private const val MAX_PARTICLES = 5000
    }

    private val particles = mutableListOf<ParticleInstance>()
    var dyingOut = false

    fun spawnParticle(particle: AbstractParticle, location: Vector3d, world: String) {
        if (dyingOut) return
        if (particles.size >= MAX_PARTICLES) return
        particles.add(ParticleInstance.fromTemplate(particle, location, world))
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
        dyingOut = true
        while (particles.isNotEmpty()) {
            update(0f)
            Thread.sleep(10)
        }
    }

    private fun getPlayersInRange(): List<Player> {
        val world = origin.world
        return Bukkit.getOnlinePlayers().filter {
            it.world == world && it.location.distanceSquared(origin) <= range * range
        }
    }
}
