package me.mochibit.defcon.explosions

import kotlinx.coroutines.*
import me.mochibit.defcon.content.explosion.effects.NuclearExplosionEffect
import me.mochibit.defcon.foundation.async.ClientCoroutineScope
import me.mochibit.defcon.foundation.async.ServerCoroutineScope
import me.mochibit.defcon.foundation.extension.inTicks
import me.mochibit.defcon.foundation.network.packet.ExplosionEffectStartPacket
import me.mochibit.defcon.foundation.registry.ModPackets
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.util.UUID
import java.util.logging.Level
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
    private val active = java.util.concurrent.ConcurrentHashMap<UUID, Explosion>()
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
                explosionUUID.toString(), "nuclear",
                epicenter, level.gameTime, 2.minutes.inTicks()
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
//        parallel(
//            ExplosionScope.SERVER to {
//                EntityShockwave(
//                    center, config.shockwaveConfig.baseHeight, config.craterConfig.baseRadius / 6,
//                    config.shockwaveConfig.baseRadius, config.craterConfig.baseRadius / 6, 50f,
//                ).process()
//            },
//            ExplosionScope.SERVER to {
//                killPlayersInCrater(center, config)
//                Crater(center, config.craterConfig.baseRadius, config.craterConfig.baseDepth, config.craterConfig.baseRadius).create()
//                Shockwave(center, config.craterConfig.baseRadius, config.shockwaveConfig.baseRadius, config.shockwaveConfig.baseHeight)
//                    .explode().join()
//            },
//        )
    }
}