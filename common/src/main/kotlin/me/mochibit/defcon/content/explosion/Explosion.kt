package me.mochibit.defcon.content.explosion

import kotlinx.coroutines.*
import me.mochibit.defcon.content.explosion.effects.NuclearExplosionParams
import me.mochibit.defcon.content.explosion.processor.Crater
import me.mochibit.defcon.content.explosion.processor.EntityDamageShockwave
import me.mochibit.defcon.explosion.processor.Shockwave
import me.mochibit.defcon.foundation.async.ClientCoroutineScope
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.async.withMainContext
import me.mochibit.defcon.foundation.network.packet.ExplosionEffectStartPacket
import me.mochibit.defcon.foundation.registry.ModDamageSources
import me.mochibit.defcon.foundation.registry.ModPackets
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

enum class ExplosionScope { SERVER, CLIENT }

fun interface ExplosionPhase {
    suspend fun run(ctx: ExplosionContext)
}

class ExplosionContext(
    val serverLevel: ServerLevel,
    val epicenter: BlockPos,
    val serverScope: CoroutineScope,
    val clientScope: CoroutineScope,
) {
    fun scopeFor(scope: ExplosionScope): CoroutineScope =
        if (scope == ExplosionScope.SERVER) serverScope else clientScope
}

abstract class Explosion(protected val level: ServerLevel, protected val center: BlockPos) {
    val id: UUID = UUID.randomUUID()
    private val serverJob = SupervisorJob(ServerCoroutineScope.coroutineContext[Job])
    private val clientJob = SupervisorJob(ClientCoroutineScope.coroutineContext[Job])

    protected val serverScope = CoroutineScope(ServerCoroutineScope.coroutineContext + serverJob)
    protected val clientScope = CoroutineScope(ClientCoroutineScope.coroutineContext + clientJob)

    private val phases = mutableListOf<ExplosionPhase>()

    protected fun step(scope: ExplosionScope, block: suspend ExplosionContext.() -> Unit) {
        phases += ExplosionPhase { ctx -> ctx.scopeFor(scope).launch { ctx.block() }.join() }
    }

    protected fun parallel(vararg branches: Pair<ExplosionScope, suspend ExplosionContext.() -> Unit>) {
        phases += ExplosionPhase { ctx ->
            branches.map { (scope, b) -> ctx.scopeFor(scope).launch { ctx.b() } }.joinAll()
        }
    }

    protected fun detached(scope: ExplosionScope, block: suspend ExplosionContext.() -> Unit) {
        phases += ExplosionPhase { ctx -> ctx.scopeFor(scope).launch { ctx.block() } }
    }


    fun trigger(): Job = serverScope.launch {
        val ctx = ExplosionContext(level, center, serverScope, clientScope)
        for (phase in phases) phase.run(ctx)
    }.also { ExplosionRegistry.register(id, this) }

    fun cancel(reason: String? = null) {
        val cause = reason?.let(::CancellationException)
        serverJob.cancel(cause)
        clientJob.cancel(cause)
        ExplosionRegistry.unregister(id)
    }
}

object ExplosionRegistry {
    private val active = ConcurrentHashMap<UUID, Explosion>()
    fun register(id: UUID, explosion: Explosion) { active[id] = explosion }
    fun unregister(id: UUID) { active.remove(id) }
    fun cancel(id: UUID) = active[id]?.cancel("manually cancelled")
    fun cancelAll(reason: String = "shutdown") = active.values.toList().forEach { it.cancel(reason) }
    fun list(): List<UUID> = active.keys.toList()
}

class NuclearExplosion(
    level: ServerLevel,
    epicenter: BlockPos,
) : Explosion(level, epicenter) {
    init {
        val explosionUUID = UUID.randomUUID();
        step(ExplosionScope.SERVER) {
            ModPackets.broadcast(ExplosionEffectStartPacket(
                explosionUUID.toString(), NuclearExplosionParams(epicenter, effectDuration = 2.minutes)
            ))
//            NuclearFogVFX(config, center).instantiate()
//            CondensationCloudVFX(config, center).instantiate()
//            ShockwaveEffect(center, config.shockwaveConfig.baseRadius, config.craterConfig.baseRadius, 50f).instantiate()
        }
//        detached(ExplosionScope.CLIENT) {
//            BlindFlashEffect(center, config.flashConfig.baseRadius, 200, 10.seconds).start()
//        }
//
//        detached(ExplosionScope.SERVER) {
//            ThermalRadiationBurn(center, config.thermalConfig.baseRadius, duration = 30.seconds).start()
//        }
//
//        detached(ExplosionScope.CLIENT) {
//            playExplosionSounds(center, config)
//      }
//        if (config.biomeHandling) {
//            detached(ExplosionScope.SERVER) {
//                handleBiomeArea(center, config)
//            }
//      }
//        step(ExplosionScope.SERVER) {
//            scheduleRadiationDelayed(center, config)
//        }
        parallel(
            ExplosionScope.SERVER to {
                EntityDamageShockwave(
                    explosionUUID = explosionUUID,
                    level = level,
                    center = Vec3(epicenter.x.toDouble(), epicenter.y.toDouble(), epicenter.z.toDouble()),
                    shockwaveHeight = 200,
                    shockwaveGroundPenetration = 20,
                    shockwaveRadius = 1000,
                    initialRadius = 100,
                    shockwaveSpeed = 50f,
                    baseDamage = 80.0,
                    damageSource = ModDamageSources.blasted(level)
                ).process()
            },
            ExplosionScope.SERVER to {
                withMainContext {
                val entities = level.getEntities(null, AABB(epicenter).inflate(100.0, 50.0, 100.0))
                val vaporized = ModDamageSources.vaporized(level)
                    entities.forEach { entity ->
                        if (entity is Player && (entity.isCreative || entity.isSpectator)) return@forEach
                        entity.hurt(vaporized, Float.MAX_VALUE)
                    }
                }

                val crater = Crater(
                    this.serverLevel,
                    epicenter,
                    100,
                    50,
                    100,
                    zoneId = explosionUUID
                )

                val radiusStart = 100 + crater.debrisRimWidth/2
                val shockwaveRadius = 1000
                val shockwaveHeight = 200

                val zoneSave = BlastZoneSavedData.get(this.serverLevel)
                val zone = BlastZone(explosionUUID, epicenter.x, epicenter.y, epicenter.z, shockwaveRadius, radiusStart, shockwaveHeight)
                zoneSave.addZone(zone)

                Shockwave(
                    explosionUUID,
                    this.serverLevel,
                    epicenter,
                    radiusStart,
                    shockwaveRadius,
                    shockwaveHeight = shockwaveHeight,
                ).explode()

                crater.create()
            },
        )
    }
}