package com.mochibit.defcon.particles


import com.mochibit.defcon.particles.templates.AbstractParticle
import com.mochibit.defcon.particles.templates.GenericParticleProperties
import com.mochibit.defcon.utils.ColorUtils
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Player
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
    private val particleID = Random.nextInt(Int.MAX_VALUE)
    private val particleUUID = UUID.randomUUID()
    private var summoned = false
    private var updatedLoc = false

    fun applyForce(force: Vector3f) {
        acceleration = acceleration.add(force)
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
        if (acceleration.lengthSquared() > 0)
            velocity = velocity.add(acceleration)

//            if (velocity.lengthSquared() > 0) {
//                val dampingForce = damping.mul(velocity).mul(-1f)
//                applyForce(dampingForce)
//            }

//        location.add(
//            (velocity.x * delta),
//            (velocity.y * delta),
//            (velocity.z * delta)
//        )
//
        //particleAdapter.updatePosition(particleID, location, players)


        location.add(velocity.x*delta, velocity.y*delta, velocity.z*delta)
        if (life % 20 == 0) {
            particleAdapter.setMotionTime(particleID, 59, players)
            particleAdapter.updatePosition(particleID, location, players)
        }

        life += 1

    }

    fun isDead() = life >= particleProperties.maxLife

    companion object {
        /**
         * Randomizes the brightness of a given color.
         *
         * @param color The original color.
         * @param darkenMax The maximum darken factor.
         * @param darkenMin The minimum darken factor.
         * @param lightenMax The maximum lighten factor.
         * @param lightenMin The minimum lighten factor.
         * @return The color with adjusted brightness.
         */
        private fun randomizeColorBrightness(
            color: Color,
            darkenMax: Double, darkenMin: Double,
            lightenMax: Double, lightenMin: Double
        ): Color {
            val factor: Double
            if (Random.nextBoolean()) {
                if (darkenMax == 0.0 && darkenMin == 0.0) return color
                factor = if (darkenMin == darkenMax) {
                    darkenMin
                } else {
                    Random.nextDouble(darkenMin, darkenMax)
                }
                return ColorUtils.darkenColor(color, factor)
            } else {
                if (lightenMax == 0.0 && lightenMin == 0.0) return color
                factor = if (lightenMin == lightenMax) {
                    lightenMin
                } else {
                    Random.nextDouble(lightenMin, lightenMax)
                }
                return ColorUtils.lightenColor(color, factor)
            }
        }

        fun fromTemplate(particleTemplate: AbstractParticle, location: Location): ParticleInstance {
            val particleAdapter = particleTemplate.getAdapter()
            val particleProperties = particleTemplate.particleProperties.clone().apply {
                color = color?.let { baseColor ->
                    var adjustedColor = particleTemplate.colorSupplier?.invoke() ?: baseColor
                    if (particleTemplate.randomizeColorBrightness) {
                        adjustedColor = randomizeColorBrightness(
                            adjustedColor,
                            particleTemplate.colorDarkenFactorMax,
                            particleTemplate.colorDarkenFactorMin,
                            particleTemplate.colorLightenFactorMax,
                            particleTemplate.colorLightenFactorMin
                        )
                    }
                    adjustedColor
                }
            }
            particleTemplate.locationConsumer?.invoke(location)

            return ParticleInstance(
                Vector3f(location.x.toFloat(), location.y.toFloat(), location.z.toFloat()),
                location.world!!.name,
                particleProperties,
                particleAdapter,
                particleTemplate.initialVelocity,
                particleTemplate.initialDamping,
                particleTemplate.initialAcceleration
            )
        }
    }
}
