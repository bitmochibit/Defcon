package me.mochibit.defcon.foundation.particles

import kotlinx.coroutines.*
import me.mochibit.defcon.content.explosion.particle.ExplosionDustParticleOptions
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.particles.shape.EmitterShape
import me.mochibit.defcon.foundation.particles.shape.PointShape
import me.mochibit.defcon.foundation.particles.shape.mutator.ShapeMutator
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class ParticleSpawnTemplate(
    val scale: Float = 1.0f,
    val speed: Float = 1.0f,
    val initialVelocity: Vector3f = Vector3f(0f, 0f, 0f),

    val initialTemperature: Float = 3000f,
    val coolingRate: Float = 400f,
    val ambientTemperature: Float = 0f,
    val colorOverride: Vector3f? = null,

    val baseLifetime: Int = 40,
    val randomLifetime: Int = 20,
    val friction: Float = 0.95f,
    val gravity: Float = 0.0f,
    val maxSpeed: Float = 0.5f,
    val hasCollision: Boolean = true,
    val speedUpWhenYMotionIsBlocked: Boolean = true,
    val shrinkOverTime: Boolean = true,
)

class ParticleEmitter<T : EmitterShape>(
    val emitterScope: CoroutineScope,
    val level: ServerLevel,
    var origin: Vec3,
    val emitterShape: T,
    val transform: Matrix4d = Matrix4d(),
    val templates: MutableList<ParticleSpawnTemplate> = mutableListOf(),
    var shapeMutator: ShapeMutator? = null,
    var positionTemperatureFn: ((Vector3d, T) -> Float)? = null,
) {

    var visible = true
    var spawnRate = 5

    val radialVelocity = Vector3f(0f, 0f, 0f)

    var baseTemperature = 3000f
        private set
    var baseCoolingRate = 15f
    var minBaseTemperature = 0f

    private var job: Job? = null
    private var lastTickMillis = 0L

    fun start() {
        lastTickMillis = System.currentTimeMillis()
        job = emitterScope.launch {
            try {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val dtSeconds = (now - lastTickMillis) / 1000f
                    lastTickMillis = now

                    if (baseTemperature > minBaseTemperature) {
                        baseTemperature = (baseTemperature - baseCoolingRate * dtSeconds)
                            .coerceAtLeast(minBaseTemperature)
                    }

                    if (visible) spawnBatch()
                    delay(50L.milliseconds)
                }
            } catch (e: CancellationException) {
                // expected
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun setVisibilityAfterDelay(visible: Boolean, delay: Duration) = apply {
        emitterScope.launch {
            delay(delay)
            this@ParticleEmitter.visible = visible
        }
    }

    fun translate(translation: Vector3f) = apply {
        transform.translate(translation.x.toDouble(), translation.y.toDouble(), translation.z.toDouble())
    }

    fun rotate(axis: Vector3f, angle: Double) = apply {
        transform.rotate(angle, axis.x.toDouble(), axis.y.toDouble(), axis.z.toDouble())
    }

    fun applyRadialVelocityFromCenter(velocity: Vector3f) = apply {
        radialVelocity.set(velocity)
    }

    private fun spawnBatch() {
        if (templates.isEmpty()) return

        repeat(spawnRate) {
            val template = templates.random()
            val local = Vector3d(0.0, 0.0, 0.0)

            if (emitterShape !is PointShape) {
                emitterShape.maskLoc(local)
                shapeMutator?.mutateLoc(local)
            }
            transform.transformPosition(local)

            val worldX = origin.x + local.x
            val worldY = origin.y + local.y
            val worldZ = origin.z + local.z

            var vx = template.initialVelocity.x().toDouble()
            var vy = template.initialVelocity.y().toDouble()
            var vz = template.initialVelocity.z().toDouble()

            if (radialVelocity.lengthSquared() > 0f) {
                val len = local.length()
                if (len > 1e-4) {
                    vx += (local.x / len) * radialVelocity.x()
                    vy += (local.y / len) * radialVelocity.y()
                    vz += (local.z / len) * radialVelocity.z()
                }
            }

            val temperature = template.colorOverride?.let { 0f }
                ?: (positionTemperatureFn?.invoke(local, emitterShape) ?: baseTemperature)

            val options = ExplosionDustParticleOptions(
                scale = template.scale,
                speed = template.speed,
                initialTemperature = temperature,
                coolingRate = template.coolingRate,
                ambientTemperature = template.ambientTemperature,
                colorOverride = template.colorOverride,
                baseLifetime = template.baseLifetime,
                randomLifetime = template.randomLifetime,
                friction = template.friction,
                gravity = template.gravity,
                maxSpeed = template.maxSpeed,
                hasCollision = template.hasCollision,
                speedUpWhenYMotionIsBlocked = template.speedUpWhenYMotionIsBlocked,
                shrinkOverTime = template.shrinkOverTime,
            )

            level.sendParticles(options, worldX, worldY, worldZ, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    fun getShape(): T = emitterShape
}