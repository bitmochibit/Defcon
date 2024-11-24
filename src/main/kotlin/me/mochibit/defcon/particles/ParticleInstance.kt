package me.mochibit.defcon.particles


import me.mochibit.defcon.particles.templates.AbstractParticle
import me.mochibit.defcon.particles.templates.GenericParticleProperties
import me.mochibit.defcon.utils.ColorUtils
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.*
import kotlin.random.Random

class ParticleInstance(
    val location: Vector3f = Vector3f(0f, 0f, 0f),
    val world: String = "",
    private val particleProperties: GenericParticleProperties,
    private val particleAdapter: ParticleAdapter,
    private var velocity: Vector3f = Vector3f(0f, 0f, 0f), // Use a shared zero vector
    private var damping: Vector3f = Vector3f(0f, 0f, 0f),  // Use a shared zero vector
    private var acceleration: Vector3f = Vector3f(0f, 0f, 0f)  // Use a shared zero vector
) {

    private var life: Int = 0
    private val particleID: Int by lazy { Random.nextInt(Int.MAX_VALUE) }
    private val particleUUID: UUID by lazy { UUID.randomUUID() }
    private var summoned = false

    fun applyForce(force: Vector3f) {
        acceleration = acceleration.add(force)
    }

    fun applyVelocity(velocity: Vector3f) {
        this.velocity = velocity
    }

    fun show(players: List<Player>) {
        if (!summoned) {
            particleAdapter.summon(location, particleProperties, players, particleID, particleUUID)
            summoned = true
        }
    }

    fun remove(players: List<Player>) {
        particleAdapter.remove(particleID, players)
    }

    fun update(delta: Float, players: List<Player>) {
        if (acceleration.lengthSquared() > 1e-6)
            velocity = velocity.add(acceleration)


        location.add(velocity.x*delta, velocity.y*delta, velocity.z*delta)
        if (life % 20 == 0 && summoned) {
            particleAdapter.setMotionTime(particleID, 59, players)
            particleAdapter.updatePosition(particleID, location, players)
        }

        life += 1

    }

    fun isDead() = life >= particleProperties.maxLife

    companion object {
        fun fromTemplate(particleTemplate: AbstractParticle, location: Vector3f, worldName: String): ParticleInstance {
            val particleAdapter = particleTemplate.particleAdapter
            val particleProperties = particleTemplate.particleProperties.clone().apply {
                color = adjustColor(this.color?: Color.RED, particleTemplate)
            }

            if (particleTemplate.randomizeScale) {
                val scale = particleProperties.scale
                // Get a random scale from the base scale (slightly smaller or larger)
                val scaleRandom = Random.nextDouble(0.9, 1.3).toFloat()
                particleProperties.scale = scale.mul(scaleRandom)
            }

            return ParticleInstance(
                Vector3f(location.x.toFloat(), location.y.toFloat(), location.z.toFloat()),
                worldName,
                particleProperties,
                particleAdapter,
                particleTemplate.initialVelocity,
                particleTemplate.initialDamping,
                particleTemplate.initialAcceleration
            )
        }
        private fun adjustColor(color: Color, template: AbstractParticle): Color {
            var adjustedColor = template.colorSupplier?.invoke() ?: color
            if (template.randomizeColorBrightness) {
                adjustedColor = ColorUtils.randomizeColorBrightness(
                    adjustedColor,
                    template.colorDarkenFactorMax,
                    template.colorDarkenFactorMin,
                    template.colorLightenFactorMax,
                    template.colorLightenFactorMin
                )
            }
            return adjustedColor
        }
    }
}
