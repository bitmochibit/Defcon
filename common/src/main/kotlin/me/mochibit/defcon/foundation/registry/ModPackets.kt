package me.mochibit.defcon.foundation.registry

import me.mochibit.defcon.foundation.info
import me.mochibit.defcon.foundation.network.packet.ModPacket
import me.mochibit.defcon.foundation.services.NetworkService
import me.mochibit.defcon.foundation.services.networkService
import net.minecraft.core.Registry
import kotlin.reflect.KClass

object ModPackets : CommonRegistry, NetworkService by networkService {
    val packetClasses: List<KClass<out ModPacket>> =
        mutableListOf<KClass<out ModPacket>>().apply {
            fun collectSubclasses(kClass: KClass<out ModPacket>) {
                if (kClass.isSealed) {
                    kClass.sealedSubclasses.forEach { collectSubclasses(it) }
                } else {
                    add(kClass)
                }
            }
            collectSubclasses(ModPacket::class)
        }

    override fun register(registry: Registry<*>?) {
        "Loading Mod Packets".info()
    }
}