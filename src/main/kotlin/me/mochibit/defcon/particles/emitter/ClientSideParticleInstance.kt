/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.particles.emitter

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import me.mochibit.defcon.particles.templates.DisplayParticleProperties
import org.bukkit.entity.Player
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.*

/**
 * Client-side particle instance that handles packets and display logic
 * Optimized for handling large numbers of particles
 */
abstract class ClientSideParticleInstance(
    particleProperties: DisplayParticleProperties,
    position: Vector3d,
    velocity: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
    damping: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
    acceleration: Vector3f = Vector3f(0.0f, 0.0f, 0.0f)
) : ParticleInstance(particleProperties, position, velocity, damping, acceleration) {

    companion object {
        fun destroyParticlesInBatch(
            player: Player,
            particles: Collection<ParticleInstance>,
        ) {
            val packetAPI = PacketEvents.getAPI().playerManager
            val destroyPacket = WrapperPlayServerDestroyEntities(
                *(particles.filterIsInstance<ClientSideParticleInstance>().map { it.particleID }.toIntArray())
            )
            packetAPI.sendPacket(player, destroyPacket)
        }
    }


    private val destroyPacket by lazy { WrapperPlayServerDestroyEntities(particleID) }

    /**
     * Get display metadata list - shared by all display types
     */
    private val displayMetadataList by lazy {
        mutableListOf(
            EntityData(8, EntityDataTypes.INT, particleProperties.interpolationDelay),
            EntityData(9, EntityDataTypes.INT, particleProperties.interpolationDuration),
            EntityData(10, EntityDataTypes.INT, 20),

            EntityData(11, EntityDataTypes.VECTOR3F, particleProperties.translation.toPacketWrapper()),
            EntityData(12, EntityDataTypes.VECTOR3F, particleProperties.scale.toPacketWrapper()),
            EntityData(13, EntityDataTypes.QUATERNION, particleProperties.rotationLeft.toPacketWrapper()),
            EntityData(14, EntityDataTypes.QUATERNION, particleProperties.rotationRight.toPacketWrapper()),

            EntityData(15, EntityDataTypes.BYTE, particleProperties.billboard.ordinal.toByte()),

            EntityData(
                16,
                EntityDataTypes.INT,
                (particleProperties.brightness.blockLight shl 4) or (particleProperties.brightness.skyLight shl 20)
            ),

            EntityData(17, EntityDataTypes.FLOAT, particleProperties.viewRange),
            EntityData(18, EntityDataTypes.FLOAT, particleProperties.shadowRadius),
            EntityData(19, EntityDataTypes.FLOAT, particleProperties.shadowStrength),
            EntityData(20, EntityDataTypes.FLOAT, particleProperties.width),
            EntityData(21, EntityDataTypes.FLOAT, particleProperties.height),

            EntityData(22, EntityDataTypes.INT, -1),
        ).apply {
            addAll(getMetadataList())
        }
    }


    override fun show(player: Player) {
        sendSpawnPacket(player)
    }

    override fun hide(player: Player) {
        sendDespawnPacket(player)
    }

    override fun updatePosition(player: Player) {
        sendPositionPacket(player)
    }

    /**
     * Send spawn packet to player - optimized for large numbers of particles
     */
    fun sendSpawnPacket(player: Player) {
        // Create spawn packet
        val spawnPacket = WrapperPlayServerSpawnEntity(
            particleID,
            Optional.of(particleUUID),
            getEntityType(),
            position.toPacketWrapper(),
            0f, 0f, 0f,
            0,
            Optional.empty()
        )

        // Send packets
        val packetAPI = PacketEvents.getAPI().playerManager
        packetAPI.sendPacket(player, spawnPacket)

        // Send metadata immediately after spawn
        sendMetadataPacket(player)
    }

    /**
     * Send metadata update packet to player
     * Only send when needed, as metadata is expensive
     */
    private fun sendMetadataPacket(player: Player) {
        val metadataPacket = WrapperPlayServerEntityMetadata(
            particleID,
            displayMetadataList,
        )

        PacketEvents.getAPI().playerManager.sendPacket(player, metadataPacket)
    }

    /**
     * Send position update packet to player
     * Optimized to reduce packet load for large particle counts
     */
    private fun sendPositionPacket(player: Player) {
        // Update position data
        val positionPacket = WrapperPlayServerEntityTeleport(
            particleID,
            position.toPacketWrapper(),
            0f, 0f,
            false
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, positionPacket)
    }

    /**
     * Send despawn packet to player
     */
    fun sendDespawnPacket(player: Player) {
        PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)
    }

    /**
     * Get particle-specific metadata
     */
    protected abstract fun getMetadataList(): List<EntityData>

    /**
     * Get entity type for this particle
     */
    protected abstract fun getEntityType(): EntityType
}