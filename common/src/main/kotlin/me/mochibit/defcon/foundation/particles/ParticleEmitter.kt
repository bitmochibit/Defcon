package me.mochibit.defcon.foundation.particles

import kotlinx.coroutines.*
import me.mochibit.defcon.content.explosion.particle.ExplosionDustParticleOptions
import me.mochibit.defcon.foundation.particles.shape.EmitterShape
import me.mochibit.defcon.foundation.particles.shape.PointShape
import me.mochibit.defcon.foundation.particles.shape.mutator.ShapeMutator
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface ParticleSpawner {
    fun spawn(
        options: ExplosionDustParticleOptions,
        x: Double, y: Double, z: Double,
        vx: Double, vy: Double, vz: Double
    )
}

fun serverParticleSpawner(level: ServerLevel): ParticleSpawner =
    ParticleSpawner { options, x, y, z, vx, vy, vz ->
        val origin = Vec3(x, y, z)
        for (player in level.players()) {
            if (player.blockPosition().closerToCenterThan(origin, 512.0)) {
                level.sendParticles(player, options, true, x, y, z,
                    0, vx, vy, vz, 1.0)
            }
        }
    }

fun clientParticleSpawner(level: net.minecraft.client.multiplayer.ClientLevel): ParticleSpawner =
    ParticleSpawner { options, x, y, z, vx, vy, vz ->
        level.addParticle(options, true, x, y, z, vx, vy, vz)
    }

class ParticleEmitter<T : EmitterShape>(
    val emitterScope: CoroutineScope,
    val spawner: ParticleSpawner,
    var origin: Vec3,
    val emitterShape: T,
    val transform: Matrix4d = Matrix4d(),
    val templates: MutableList<ParticleSpawnTemplate> = mutableListOf(),
    var shapeMutator: ShapeMutator? = null,
    var positionTemperatureFn: ((Vector3d, T) -> Float)? = null,
) {

    var visible = true
    var spawnRate = 10

    val radialVelocity = Vector3f(0f, 0f, 0f)

    var baseTemperature = 3000f
        private set
    var baseCoolingRate = 15f
    var minBaseTemperature = 0f


    fun step(deltaTime: Float) {
        if (baseTemperature > minBaseTemperature) {
            baseTemperature = (baseTemperature - baseCoolingRate * deltaTime)
                .coerceAtLeast(minBaseTemperature)
        }
        if (visible) spawnBatch()
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

            spawner.spawn(options, worldX, worldY, worldZ, vx, vy, vz)
        }
    }

    fun getShape(): T = emitterShape
}