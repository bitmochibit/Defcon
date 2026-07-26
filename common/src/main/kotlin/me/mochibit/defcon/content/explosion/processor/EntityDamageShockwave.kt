package me.mochibit.defcon.content.explosion.processor

import kotlinx.coroutines.delay
import me.mochibit.defcon.foundation.services.eventService
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

object ShockwaveRegistry {
    private val active = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<EntityDamageShockwave>>()

    fun register(level: ServerLevel, shockwave: EntityDamageShockwave) {
        active.computeIfAbsent(level.dimension()) { CopyOnWriteArrayList() }.add(shockwave)
    }

    fun unregister(level: ServerLevel, shockwave: EntityDamageShockwave) {
        active[level.dimension()]?.remove(shockwave)
    }

    fun getActive(level: Level): List<EntityDamageShockwave> =
        active[level.dimension()] ?: emptyList()
}

/**
 * Not for block destruction but this is solely used for damaging entities since well, shockwave hella hurt
 */
class EntityDamageShockwave(
    private val level: ServerLevel,
    val center: Vec3,
    val shockwaveHeight: Int,
    val shockwaveGroundPenetration: Int,
    val shockwaveRadius: Int,
    val initialRadius: Int = 0,
    val shockwaveSpeed: Float = 50f,
    val baseDamage: Double = 80.0,
    val damageSource: DamageSource
) {
    companion object {
        private val listenerRegistered = AtomicBoolean(false)

        private fun ensureListenerRegistered() {
            if (!listenerRegistered.compareAndSet(false, true)) return

            eventService.onEntityTick { entity ->
                if (entity !is LivingEntity) return@onEntityTick
                val entityLevel = entity.level() as? ServerLevel ?: return@onEntityTick

                val activeShockwaves = ShockwaveRegistry.getActive(entityLevel)
                if (activeShockwaves.isEmpty()) return@onEntityTick

                val gameTime = entityLevel.gameTime
                for (shockwave in activeShockwaves) {
                    shockwave.tryAffect(entity, gameTime)
                }
            }
        }
    }

    private val processedEntities: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())
    private val startGameTime: Long = level.gameTime
    private val isCleanedUp = AtomicBoolean(false)

    fun isFinished(currentGameTime: Long): Boolean =
        currentRadius(currentGameTime) >= shockwaveRadius

    fun currentRadius(currentGameTime: Long): Int {
        val elapsedTicks = (currentGameTime - startGameTime).coerceAtLeast(0)
        val elapsedSeconds = elapsedTicks / 20f
        return min(shockwaveRadius.toFloat(), initialRadius + shockwaveSpeed * elapsedSeconds).toInt()
    }

    fun tryAffect(entity: LivingEntity, currentGameTime: Long) {
        if (!entity.isAlive || processedEntities.contains(entity.uuid)) return

        val radius = currentRadius(currentGameTime)
        val previousRadius = currentRadius(currentGameTime - 1)
        val pos = entity.position()
        val distance = pos.distanceTo(center)

        if (distance > radius || distance <= previousRadius) return
        if (pos.y < center.y - shockwaveGroundPenetration || pos.y > center.y + shockwaveHeight) return

        if (!processedEntities.add(entity.uuid)) return

        applyEffect(entity, distance)
    }

    private fun applyEffect(entity: LivingEntity, distance: Double) {
        val power = ((shockwaveRadius - distance) / shockwaveRadius).coerceIn(0.0, 1.0).toFloat()
        val scaledDamage = (baseDamage * power).toFloat()
        entity.hurt(damageSource, scaledDamage)

        val knockback = calculateKnockbackVector(entity.position(), power)
        entity.deltaMovement = knockback
        entity.hasImpulse = true

        if (entity is ServerPlayer) {
            entity.connection.send(ClientboundSetEntityMotionPacket(entity))
        }
    }

    private fun calculateKnockbackVector(entityPos: Vec3, explosionPower: Float): Vec3 {
        val dx = entityPos.x - center.x
        val dz = entityPos.z - center.z
        val dy = entityPos.y - center.y
        val horizontalDistance = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
        val knockbackPower = explosionPower * 2
        return Vec3(
            knockbackPower * (dx / horizontalDistance),
            if (dy != 0.0) knockbackPower / (abs(dy) * 2) + 1.2 else 1.2,
            knockbackPower * (dz / horizontalDistance)
        )
    }

    /** Registra la shockwave e resta sospesa finché non termina la sua durata. */
    suspend fun process() {
        ensureListenerRegistered()
        ShockwaveRegistry.register(level, this)
        try {
            val durationSeconds = (shockwaveRadius - initialRadius) / shockwaveSpeed
            delay((durationSeconds * 1000).toLong().milliseconds)
        } finally {
            cleanup()
        }
    }

    suspend fun forceCleanup() = cleanup()

    private fun cleanup() {
        if (!isCleanedUp.compareAndSet(false, true)) return
        ShockwaveRegistry.unregister(level, this)
    }
}