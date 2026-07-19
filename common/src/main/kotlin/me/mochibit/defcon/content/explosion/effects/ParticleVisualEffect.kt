package me.mochibit.defcon.content.explosion.effects

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.mochibit.defcon.foundation.async.ModDispatchers
import me.mochibit.defcon.foundation.particles.ParticleSpawner
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Serializable
sealed interface EffectParams {
    val center: BlockPos
    val effectDuration: Duration
}

abstract class ParticleVisualEffect<T : EffectParams>(
    protected val params: T,
    protected val particleSpawner: ParticleSpawner,
    protected val scope: CoroutineScope
) {
    private var lifecycleLoop: Job? = null
    private var lastTickMillis = 0L

    private fun isGamePaused(): Boolean =
        Minecraft.getInstance().isPaused

    fun start() {
        if (lifecycleLoop?.isActive == true) return
        lastTickMillis = System.currentTimeMillis()

        val totalActiveMillis = params.effectDuration.inWholeMilliseconds
        var elapsedActiveMillis = 0L

        lifecycleLoop = scope.launch(ModDispatchers.Client()) {
            onStart()
            try {
                while (isActive && elapsedActiveMillis < totalActiveMillis) {
                    if (isGamePaused()) {
                        lastTickMillis = System.currentTimeMillis()
                        delay(50.milliseconds)
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val dt = ((now - lastTickMillis) / 1000f).coerceAtLeast(0f)
                    lastTickMillis = now
                    elapsedActiveMillis += (dt * 1000f).toLong()

                    onStep(dt)
                    delay(50.milliseconds)
                }
            } finally {
                onStop()
            }
        }
    }

    fun step(deltaTime: Float) {
        onStep(deltaTime)
    }

    fun stop() {
        lifecycleLoop?.cancel()
    }

    fun fastForward(ticks: Long) {
        repeat(ticks.toInt().coerceAtMost(2000)) {
            onStep(0f)
        }
    }

    protected abstract fun onStart()
    protected abstract fun onStop()
    protected abstract fun onStep(deltaTime: Float)
}