package me.mochibit.defcon.content.explosion.client

import me.mochibit.defcon.content.explosion.effects.EffectParams
import me.mochibit.defcon.content.explosion.effects.EffectRegistry
import me.mochibit.defcon.foundation.async.ClientCoroutineScope
import me.mochibit.defcon.foundation.async.launchOnClient
import me.mochibit.defcon.foundation.extension.toVec3
import me.mochibit.defcon.foundation.particles.clientParticleSpawner
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.UUID

fun handleParticleEffectStart(
    effectId: String,
    params: EffectParams
) {
    launchOnClient {
        val effectUuid = UUID.fromString(effectId)

        val clientLevel = Minecraft.getInstance().level ?: return@launchOnClient
    //    val elapsedTicks = clientLevel.gameTime - startGameTime
    //    val remainingTicks = (effectDurationTicks - elapsedTicks).coerceAtLeast(0)
    //    if (remainingTicks <= 0) return

        val spawner = clientParticleSpawner(clientLevel)

        val effect = EffectRegistry.build(params, spawner, ClientCoroutineScope)


        ClientVisualEffects.register(effectUuid, effect)
    //    effect.fastForward(elapsedTicks)
        effect.start()

    }

}

fun handleShockwaveReachEffect(
    explosionId: String,
    power: Float,
    center: Vec3
) {
    val at = Minecraft.getInstance().player?.position() ?: return
    ExplosionSoundManager.playSounds(ExplosionSoundManager.DefaultSounds.ShockwaveHitSound, at)
    CameraShake(
        CameraShakeOptions(
            magnitude = 2.6f,
            decay = 0.04f,
            pitchPeriod = 3.7f * power,
            yawPeriod = 3.0f * power
        ),
        origin = center

    )
}
