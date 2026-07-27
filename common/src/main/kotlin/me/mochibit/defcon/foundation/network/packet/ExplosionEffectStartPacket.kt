package me.mochibit.defcon.foundation.network.packet

import kotlinx.serialization.Serializable
import me.mochibit.defcon.content.explosion.client.handleParticleEffectStart
import me.mochibit.defcon.content.explosion.effects.EffectParams

@Serializable
class ExplosionEffectStartPacket(
    val effectId: String,
    val params: EffectParams,
) : ModPacket, S2CPacket
{
    override fun handle(context: ModPacket.Context): Boolean {
        handleParticleEffectStart(effectId, params)
        return true;
    }
}


