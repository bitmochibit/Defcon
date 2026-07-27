package me.mochibit.defcon.foundation.network.packet

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.defcon.content.explosion.client.handleParticleEffectStart
import me.mochibit.defcon.content.explosion.client.handleShockwaveReachEffect
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

@Serializable
class ShockwaveReachEffectPacket(
    val explosionUUID: String,
    val power: Float,
    @Contextual val center: Vec3,
) : ModPacket, S2CPacket
{
    override fun handle(context: ModPacket.Context): Boolean {
        handleShockwaveReachEffect(explosionUUID, power, center)
        return true;
    }
}