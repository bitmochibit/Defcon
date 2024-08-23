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

package com.mochibit.defcon.particles

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.templates.DisplayParticleProperties
import com.mochibit.defcon.particles.templates.GenericParticleProperties
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.function.Consumer

enum class RegistryName {
    Vector3f, Quatenionf, Byte, Int, Float, ItemStack
}


abstract class  DisplayEntityParticleAdapter() : ParticleAdapter() {
    companion object {
        val cachedSerializers = mutableMapOf<RegistryName, WrappedDataWatcher.Serializer>(
            RegistryName.Vector3f to WrappedDataWatcher.Registry.get(Vector3f::class.java),
            RegistryName.Quatenionf to WrappedDataWatcher.Registry.get(Quaternionf::class.java),
            RegistryName.Byte to WrappedDataWatcher.Registry.get(Byte::class.javaObjectType),
            RegistryName.Int to WrappedDataWatcher.Registry.get(Int::class.javaObjectType),
            RegistryName.Float to WrappedDataWatcher.Registry.get(Float::class.javaObjectType),
            RegistryName.ItemStack to WrappedDataWatcher.Registry.getItemStackSerializer(false)
        )
    }

    abstract fun getSpawnPacket(displayID: Int, displayUUID: UUID, location: Vector3f): PacketContainer

    override fun summon(location: Vector3f, particleProperties: GenericParticleProperties, players: List<Player>, displayID: Int, displayUUID: UUID){
        if (particleProperties !is DisplayParticleProperties) {
            throw IllegalArgumentException("DisplayEntitySpawnStrategy requires DisplayParticleProperties")
        }

        val spawnPacket = getSpawnPacket(displayID, displayUUID, location)
        sendPacketToAll(spawnPacket, players)
        applyMetadata(displayID, particleProperties, players)
    }
    protected abstract fun applyMetadata(displayID: Int, particleProperties: DisplayParticleProperties, players: List<Player>)

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
        packet.doubles.write(0, newLocation.x.toDouble())
        packet.doubles.write(1, newLocation.y.toDouble())
        packet.doubles.write(2, newLocation.z.toDouble())
        sendPacketToAll(packet, players)
    }

    protected fun sendMetadata(displayID: Int, wrappedDataValues: List<WrappedDataValue>, players: List<Player>) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
        packet.modifier.writeDefaults()
        packet.integers.write(0, displayID)

        packet.dataValueCollectionModifier.write(0, wrappedDataValues)

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