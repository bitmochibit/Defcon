/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.particles

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import me.mochibit.defcon.particles.templates.DisplayParticleProperties
import me.mochibit.defcon.particles.templates.GenericParticleProperties
import org.bukkit.entity.Player
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*


enum class RegistryName {
    Vector3f, Quatenionf, Byte, Int, Float, TextComponent, OptionalTextComponent, NBTCompound
}


abstract class DisplayEntityParticleAdapter<T: DisplayParticleProperties>(private var properties: T) : ParticleAdapter{
    companion object {
        val cachedSerializers = mutableMapOf<RegistryName, WrappedDataWatcher.Serializer>(
            RegistryName.Vector3f to WrappedDataWatcher.Registry.get(Vector3f::class.java),
            RegistryName.Quatenionf to WrappedDataWatcher.Registry.get(Quaternionf::class.java),
            RegistryName.Byte to WrappedDataWatcher.Registry.get(Byte::class.javaObjectType),
            RegistryName.Int to WrappedDataWatcher.Registry.get(Int::class.javaObjectType),
            RegistryName.Float to WrappedDataWatcher.Registry.get(Float::class.javaObjectType),
            RegistryName.TextComponent to WrappedDataWatcher.Registry.getChatComponentSerializer(false),
            RegistryName.OptionalTextComponent to WrappedDataWatcher.Registry.getChatComponentSerializer(true),
            RegistryName.NBTCompound to WrappedDataWatcher.Registry.getNBTCompoundSerializer(),
        )
    }

    abstract fun getSpawnPacket(displayID: Int, displayUUID: UUID, location: Vector3f): PacketContainer

    override fun summon(
        location: Vector3f,
        players: List<Player>,
        displayID: Int,
        displayUUID: UUID
    ) {
        val spawnPacket = getSpawnPacket(displayID, displayUUID, location)
        sendPacketToAll(spawnPacket, players)
        applyMetadata(displayID, properties, players)
    }

    override fun summon(
        location: Vector3f,
        particleProperties: GenericParticleProperties,
        players: List<Player>,
        displayID: Int,
        displayUUID: UUID
    ) {
        val spawnPacket = getSpawnPacket(displayID, displayUUID, location)
        sendPacketToAll(spawnPacket, players)
        @Suppress("UNCHECKED_CAST")
        applyMetadata(displayID, particleProperties as T, players)
    }

    protected open fun applyMetadata(displayID: Int, particleProperties: T, players: List<Player>) {
        val brightnessValue =
            (particleProperties.brightness.blockLight shl 4) or (particleProperties.brightness.skyLight shl 20)
        val packedItems = mutableListOf(
            WrappedDataValue(8, cachedSerializers[RegistryName.Int], particleProperties.interpolationDelay),
            WrappedDataValue(9, cachedSerializers[RegistryName.Int], particleProperties.interpolationDuration),
            WrappedDataValue(10, cachedSerializers[RegistryName.Int], particleProperties.teleportDuration),
            WrappedDataValue(11, cachedSerializers[RegistryName.Vector3f], particleProperties.translation),
            WrappedDataValue(12, cachedSerializers[RegistryName.Vector3f], particleProperties.scale),
            WrappedDataValue(13, cachedSerializers[RegistryName.Quatenionf], particleProperties.rotationLeft),
            WrappedDataValue(14, cachedSerializers[RegistryName.Quatenionf], particleProperties.rotationRight),
            WrappedDataValue(
                15,
                cachedSerializers[RegistryName.Byte],
                particleProperties.billboard.ordinal.toByte()
            ),
            WrappedDataValue(16, cachedSerializers[RegistryName.Int], brightnessValue),
            WrappedDataValue(17, cachedSerializers[RegistryName.Float], particleProperties.viewRange),
            WrappedDataValue(18, cachedSerializers[RegistryName.Float], particleProperties.shadowRadius),
            WrappedDataValue(19, cachedSerializers[RegistryName.Float], particleProperties.shadowStrength),
            WrappedDataValue(20, cachedSerializers[RegistryName.Float], particleProperties.width),
            WrappedDataValue(21, cachedSerializers[RegistryName.Float], particleProperties.height),
        )
        packedItems.addAll(getSubMeta(displayID, particleProperties, players))
        sendMetadata(displayID, packedItems, players)
    }

    protected abstract fun getSubMeta(displayID: Int, particleProperties: T, players: List<Player>) : List<WrappedDataValue>
    protected fun sendMetadata(displayID: Int, wrappedDataValues: List<WrappedDataValue>, players: List<Player>) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
        packet.modifier.writeDefaults()
        packet.integers.write(0, displayID)
        packet.dataValueCollectionModifier.write(0, wrappedDataValues)
        sendPacketToAll(packet, players)
    }
    protected fun setTeleportDuration(displayID: Int, teleportDuration: Long, players: List<Player>) {
        sendMetadata(
            displayID = displayID,
            listOf(
                WrappedDataValue(
                    10,
                    cachedSerializers[RegistryName.Int],
                    teleportDuration.toInt()
                )
            ),
            players
        )
    }

    override fun setMotionTime(displayID: Int, time: Int, players: List<Player>) {
        // Clamp the time to 0 and 59 (for some fucking reason mojang????)
        setTeleportDuration(displayID, time.toLong().coerceIn(0, 59), players)
    }

    override fun remove(displayID: Int, players: List<Player>) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_DESTROY)
        packet.intLists.write(0, listOf(displayID))
        sendPacketToAll(packet, players)
    }

    override fun updatePosition(displayID: Int, newLocation: Vector3f, players: List<Player>) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT)
        packet.integers.write(0, displayID)
        with(packet.doubles) {
            write(0, newLocation.x.toDouble())
            write(1, newLocation.y.toDouble())
            write(2, newLocation.z.toDouble())
        }
        sendPacketToAll(packet, players)
    }


    protected fun sendPacketToAll(packet: PacketContainer, players: List<Player>) {
        players.forEach { sendPacket(packet, it) }
    }

    protected fun sendPacket(packet: PacketContainer, player: Player) {
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}