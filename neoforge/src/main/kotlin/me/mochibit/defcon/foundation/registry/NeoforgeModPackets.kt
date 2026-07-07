package me.mochibit.defcon.foundation.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import me.mochibit.defcon.foundation.network.FriendlyByteBufDecoder
import me.mochibit.defcon.foundation.network.FriendlyByteBufEncoder
import me.mochibit.defcon.foundation.network.packet.C2SPacket
import me.mochibit.defcon.foundation.network.packet.ModPacket
import me.mochibit.defcon.foundation.network.packet.S2CPacket
import net.minecraft.core.Registry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType


class ModPacketPayload<T : ModPacket>(
    val packet: T,
    private val payloadType: CustomPacketPayload.Type<ModPacketPayload<T>>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ModPacketPayload<T>> = payloadType
}

object NeoforgeModPackets : NeoforgeRegistry {
    private data class PayloadEntry<T : ModPacket>(
        val type: CustomPacketPayload.Type<ModPacketPayload<T>>,
        val codec: StreamCodec<RegistryFriendlyByteBuf, ModPacketPayload<T>>,
    )

    private val entries = mutableMapOf<KClass<out ModPacket>, PayloadEntry<*>>()

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1").optional()
        ModPackets.packetClasses.forEach { registerPacket(registrar, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ModPacket> registerPacket(
        registrar: PayloadRegistrar,
        packetClass: KClass<T>,
    ) {
        val isC2S = packetClass.isSubclassOf(C2SPacket::class)
        val isS2C = packetClass.isSubclassOf(S2CPacket::class)

        if (!isC2S && !isS2C) {
            throw IllegalArgumentException("Packet must implement C2SPacket or S2CPacket: $packetClass")
        }

        val serializer = serializer(packetClass.starProjectedType) as KSerializer<T>
        val type =
            CustomPacketPayload.Type<ModPacketPayload<T>>(
                ResourceLocation.fromNamespaceAndPath("createharmonics", packetClass.simpleName!!.lowercase()),
            )
        val codec =
            StreamCodec.of<RegistryFriendlyByteBuf, ModPacketPayload<T>>(
                { buf, payload -> serializer.serialize(FriendlyByteBufEncoder(buf), payload.packet) },
                { buf -> ModPacketPayload(serializer.deserialize(FriendlyByteBufDecoder(buf)), type) },
            )
        entries[packetClass] = PayloadEntry(type, codec)

        when {
            isC2S && isS2C -> {
                registrar.playToServer(type, codec) { payload, ctx ->
                    payload.packet.handle(ModPacket.Context(ctx.player() as? ServerPlayer))
                    if (payload.packet is S2CPacket) payload.packet.handleServer(ModPacket.Context(ctx.player() as? ServerPlayer))
                }
                registrar.playToClient(type, codec) { payload, ctx ->
                    payload.packet.handle(ModPacket.Context(null))
                    if (payload.packet is C2SPacket) payload.packet.handleClient(ModPacket.Context(null))
                }
            }

            isC2S -> {
                registrar.playToServer(type, codec) { payload, ctx ->
                    payload.packet.handle(ModPacket.Context(ctx.player() as? ServerPlayer))
                    if (payload.packet is S2CPacket) payload.packet.handleServer(ModPacket.Context(ctx.player() as? ServerPlayer))
                }
            }

            else -> {
                registrar.playToClient(type, codec) { payload, ctx ->
                    payload.packet.handle(ModPacket.Context(null))
                    if (payload.packet is C2SPacket) payload.packet.handleClient(ModPacket.Context(null))
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ModPacket> payloadFor(packet: T): ModPacketPayload<T> {
        val entry =
            entries[packet::class] as? PayloadEntry<T>
                ?: error("Unregistered packet class: ${packet::class}")
        return ModPacketPayload(packet, entry.type)
    }

    override fun register(registry: Registry<*>?) {
        // modBus.addListener(NeoforgeModPackets::registerPayloads)
    }
}