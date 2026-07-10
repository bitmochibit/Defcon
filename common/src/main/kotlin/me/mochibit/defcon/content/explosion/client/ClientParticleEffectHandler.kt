package me.mochibit.defcon.content.explosion.client

import me.mochibit.defcon.content.explosion.effects.EffectParams
import me.mochibit.defcon.content.explosion.effects.EffectRegistry
import me.mochibit.defcon.foundation.async.ClientCoroutineScope
import me.mochibit.defcon.foundation.async.launchOnClient
import me.mochibit.defcon.foundation.particles.clientParticleSpawner
import net.minecraft.client.Minecraft
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
