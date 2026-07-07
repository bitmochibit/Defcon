package me.mochibit.defcon.content.explosion.effects

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.minecraft.core.BlockPos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class ParticleVisualEffect(
    val center: BlockPos,
    val effectDuration: Duration,
    val scope: CoroutineScope
) {
    private var lifecycleLoop: Job? = null

    fun start() {
        if (lifecycleLoop?.isActive == true) return

        lifecycleLoop = scope.launch {
            onStart()
            try {
                withTimeout(effectDuration) {
                    while (isActive) {
                        step()
                        delay(50.milliseconds)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Effect timed out
            } finally {
                onStop()
            }
        }
    }

    fun step() {
        onStep()
    }

    fun stop() {
        lifecycleLoop?.cancel()
    }


    protected abstract fun onStart()

    protected abstract fun onStop()

    protected abstract fun onStep()
}

