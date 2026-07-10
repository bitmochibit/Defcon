package me.mochibit.defcon.foundation.network.packet

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.defcon.content.explosion.client.handleParticleEffectStart
import me.mochibit.defcon.content.explosion.effects.EffectParams
import me.mochibit.defcon.content.explosion.effects.NuclearExplosionEffect
import me.mochibit.defcon.content.explosion.effects.NuclearExplosionParams
import me.mochibit.defcon.content.explosion.effects.ParticleVisualEffect
import me.mochibit.defcon.foundation.async.ClientCoroutineScope
import me.mochibit.defcon.foundation.extension.asResource
import me.mochibit.defcon.foundation.extension.ticks
import me.mochibit.defcon.foundation.particles.clientParticleSpawner
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
class ExplosionEffectStartPacket(
    val effectId: String,
    val effectType: String,
    @Contextual val center: BlockPos,
    val startGameTime: Long,
    val effectDurationTicks: Int
) : ModPacket, S2CPacket
{
    override fun handle(context: ModPacket.Context): Boolean {
        handleParticleEffectStart(effectId, NuclearExplosionParams(center, effectDurationTicks.ticks()))
        return true;
    }
}



