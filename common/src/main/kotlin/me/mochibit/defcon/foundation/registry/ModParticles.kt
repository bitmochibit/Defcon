package me.mochibit.defcon.foundation.registry

import com.mojang.serialization.MapCodec
import com.tterrag.registrate.Registrate
import com.tterrag.registrate.util.entry.RegistryEntry
import com.tterrag.registrate.util.nullness.NonNullSupplier
import me.mochibit.defcon.ModRegistrate
import me.mochibit.defcon.content.explosion.particle.ExplosionDustParticleOptions
import me.mochibit.defcon.foundation.info
import net.minecraft.core.Registry
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
@AutoRegister
object ModParticles : Registrable {
    val ExplosionDustParticle = ModRegistrate.particle<ExplosionDustParticleOptions>(
        "explosion_particle",
        codecSupplier = { ExplosionDustParticleOptions.CODEC },
        streamCodecSupplier = { ExplosionDustParticleOptions.STREAM_CODEC }
    )

    override fun register(registry: Registry<*>?) {
        "Loading mod particles".info()
    }
    inline fun <reified T : ParticleOptions> Registrate.particle(
        name: String,
        codecSupplier: NonNullSupplier<MapCodec<T>>,
        streamCodecSupplier: NonNullSupplier<StreamCodec<in RegistryFriendlyByteBuf, T>>,
        override: Boolean = false
    ): RegistryEntry<in ParticleType<T>, out ParticleType<T>> {
        return this.simple(
            name,
            Registries.PARTICLE_TYPE
        ) {
            object : ParticleType<T>(override) {
                override fun codec(): MapCodec<T> = codecSupplier.get()
                override fun streamCodec(): StreamCodec<in RegistryFriendlyByteBuf, T> = streamCodecSupplier.get()
            }
        }
    }

}

