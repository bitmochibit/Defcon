package me.mochibit.defcon

import me.mochibit.defcon.DefconMod.MOD_ID
import me.mochibit.defcon.content.explosion.particle.ExplosionDustParticle
import me.mochibit.defcon.foundation.registry.ModParticles
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent

@Mod(MOD_ID, dist = [Dist.CLIENT])
class NeoforgeModClientEntryPoint(
    val modEventBus: IEventBus,
    val container: ModContainer,
) {
    companion object {
        @JvmStatic
        lateinit var instance: NeoforgeModClientEntryPoint
            private set
    }

    init {
        instance = this
        initialize()
    }

    fun registerParticleFactories(event: RegisterParticleProvidersEvent) {
        event.registerSpriteSet(ModParticles.ExplosionDustParticle.get()) { sheet ->
            ExplosionDustParticle.Provider(
                sheet
            )
        }
    }

    private fun initialize() {
        DefconClientMod.setup()
        modEventBus.addListener(this::registerParticleFactories)
    }
}