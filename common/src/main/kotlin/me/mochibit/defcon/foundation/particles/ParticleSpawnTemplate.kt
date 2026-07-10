package me.mochibit.defcon.foundation.particles

import org.joml.Vector3f

data class ParticleSpawnTemplate(
    val scale: Float = 1.0f,
    val speed: Float = 1.0f,
    val initialVelocity: Vector3f = Vector3f(0f, 0f, 0f),

    val initialTemperature: Float = 3000f,
    val coolingRate: Float = 400f,
    val ambientTemperature: Float = 0f,
    val colorOverride: Vector3f? = null,

    val baseLifetime: Int = 100,
    val randomLifetime: Int = 20,
    val friction: Float = 0.95f,
    val gravity: Float = 0.0f,
    val maxSpeed: Float = 0.5f,
    val hasCollision: Boolean = true,
    val speedUpWhenYMotionIsBlocked: Boolean = false,
    val shrinkOverTime: Boolean = false,
)