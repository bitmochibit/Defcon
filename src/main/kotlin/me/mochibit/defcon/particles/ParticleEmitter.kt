package me.mochibit.defcon.particles

import me.mochibit.defcon.extensions.distanceSquared
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.emitter.EmitterShape
import me.mochibit.defcon.particles.emitter.PointShape
import me.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.joml.Matrix4f
import org.joml.Vector3f

class ParticleEmitter(
    position: Location,
    private val range: Double,
    private val maxParticles: Int = 5000,
    var emitterShape: EmitterShape = PointShape,
    val transform: Matrix4f = Matrix4f(),
    val spawnableParticles: HashSet<AbstractParticle> = hashSetOf()
) : Lifecycled {

    private val origin: Vector3f = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    private val positionCursor: Vector3f = Vector3f(origin)
    val world: World = position.world


    private val particles = mutableListOf<ParticleInstance>()
    private var dyingOut = false

    val radialVelocity = Vector3f(0f, 0f, 0f)

    var visible = true
        set(value) {
            field = value
            particles.forEach { _ ->
                if (!value) {
                    val players = getPlayersInRange()
                    particles.forEach { it.remove(players) }
                }
            }
        }

    fun spawnParticle(particle: AbstractParticle) {
        if (particles.size >= maxParticles) return
        positionCursor.set(origin)
        if (emitterShape != PointShape) {
            emitterShape.maskLoc(positionCursor)
            transform.transformPosition(positionCursor)
            particles.add(ParticleInstance.fromTemplate(particle, positionCursor, world.name).apply {
                if (radialVelocity.lengthSquared() > 0) {
                    // Calculate velocity from oldOrigin to origin and apply it to the particle
                    val velocity = Vector3f(positionCursor).sub(origin).normalize().mul(radialVelocity)
                    applyVelocity(velocity)
                }
            })
        } else {
            particles.add(ParticleInstance.fromTemplate(particle, transform.transformPosition(positionCursor), world.name))
        }
    }

    override fun start() {}

    override fun update(delta: Float) {
        // Add particles in random intervals until maxParticles is reached
        if (particles.size < maxParticles && !dyingOut && visible) {
            spawnParticle(this.spawnableParticles.random())
        }

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
        return Bukkit.getOnlinePlayers().filter {
            it.world == world && it.location.distanceSquared(origin) <= range * range
        }
    }
}
