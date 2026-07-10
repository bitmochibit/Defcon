package me.mochibit.defcon.content.explosion.effects

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import me.mochibit.defcon.foundation.async.ModDispatchers
import me.mochibit.defcon.foundation.particles.ParticleSpawner
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


    fun start() {
        if (lifecycleLoop?.isActive == true) return
        lastTickMillis = System.currentTimeMillis()

        lifecycleLoop = scope.launch(ModDispatchers.Client()) {
            onStart()
            try {
                withTimeout(params.effectDuration) {
                    while (isActive) {
                        val now = System.currentTimeMillis()
                        val dt = ((now - lastTickMillis) / 1000f).coerceAtLeast(0f)
                        lastTickMillis = now

                        onStep(dt)
                        delay(50.milliseconds)
                    }
                }
            } catch (e: TimeoutCancellationException) {
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

