package me.mochibit.defcon.content.explosion.client

import me.mochibit.defcon.foundation.services.clientEventService
import me.mochibit.defcon.foundation.services.eventService
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class CameraShakeOptions(
    val magnitude: Float,
    val decay: Float,
    val pitchPeriod: Float,
    val yawPeriod: Float,
    val rollPeriod: Float = 0f,
    val maxDistance: Double = 0.0
)

class CameraShake(
    private val options: CameraShakeOptions,
    origin: Vec3? = null
) {
    private val startGameTime: Long = Minecraft.getInstance().level?.gameTime ?: 0L
    private val baseMagnitude: Float = options.magnitude * (origin?.let { falloff(it) } ?: 1f)
    var finished: Boolean = false
        private set

    init {
        if (baseMagnitude <= 0.01f) finished = true else CameraShakeManager.register(this)
    }

    private fun falloff(origin: Vec3): Float {
        if (options.maxDistance <= 0.0) return 1f
        val player = Minecraft.getInstance().player ?: return 1f
        val distance = player.position().distanceTo(origin)
        return (1.0 - min(distance / options.maxDistance, 1.0)).toFloat()
    }

    fun sample(gameTime: Long, partialTick: Double): Triple<Float, Float, Float> {
        if (finished) return Triple(0f, 0f, 0f)

        val elapsedTicks = (gameTime - startGameTime) + partialTick.toFloat()
        val magnitude = baseMagnitude - options.decay * elapsedTicks
        if (magnitude <= 0f) { finished = true; return Triple(0f, 0f, 0f) }

        val pitch = sin(elapsedTicks / options.pitchPeriod * 2 * Math.PI).toFloat() * magnitude
        val yaw = cos(elapsedTicks / options.yawPeriod * 2 * Math.PI).toFloat() * magnitude
        val roll = if (options.rollPeriod > 0f)
            sin(elapsedTicks / options.rollPeriod * 2 * Math.PI).toFloat() * magnitude * 0.5f else 0f

        return Triple(pitch, yaw, roll)
    }

    fun stop() { finished = true }
}

object CameraShakeManager {
    private val active = CopyOnWriteArrayList<CameraShake>()

    fun register(shake: CameraShake) { active.add(shake) }
    fun stopAll() { active.forEach { it.stop() }; active.clear() }

    init {
        clientEventService.onComputeCameraAngles { ctx ->
            if (active.isEmpty()) return@onComputeCameraAngles
            val level = Minecraft.getInstance().level ?: return@onComputeCameraAngles
            val gameTime = level.gameTime

            var pitchOffset = 0f; var yawOffset = 0f; var rollOffset = 0f
            for (shake in active) {
                val (p, y, r) = shake.sample(gameTime, ctx.partialTick)
                pitchOffset += p; yawOffset += y; rollOffset += r
            }
            active.removeIf { it.finished }

            ctx.pitch += pitchOffset
            ctx.yaw += yawOffset
            ctx.roll += rollOffset
        }
    }
}