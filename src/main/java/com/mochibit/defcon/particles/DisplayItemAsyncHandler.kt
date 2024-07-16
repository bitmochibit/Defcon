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
import com.comphenix.protocol.utility.MinecraftReflection
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.math.Vector3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.scheduler.BukkitRunnable
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.sqrt

enum class RegistryName {
    Vector3f, Quatenionf, Byte, Int, Float, ItemStack
}

class DisplayItemAsyncHandler(
    val loc: Location,
    val players: MutableCollection<out Player>,
    val properties: DisplayParticleProperties
) {
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
    /**
     * The random ID of the display item. It is a Variable-length data encoding a two's complement signed 32-bit integer;
     */
    val itemID = (Math.random() * Int.MAX_VALUE).toInt()
    val itemUUID = UUID.randomUUID()
    var velocity = Vector3(0.0, 0.0, 0.0); private set
    var damping = Vector3(0.1, 0.1, 0.1); private set
    var acceleration = Vector3(0.0, 0.0, 0.0); private set
    var accelerationTicks = 0; private set

    fun initialVelocity(initialVelocity: Vector3) = apply { this.velocity = initialVelocity.clone()}
    fun damping(damping: Vector3) = apply { this.damping = damping.clone() }
    fun acceleration(acceleration: Vector3) = apply { this.acceleration = acceleration.clone() }
    fun accelerationTicks(accelerationTicks: Int) = apply { this.accelerationTicks = accelerationTicks }

    /**
     * Sends the packet to the players inside the list, to display the item
     */
    fun summon(): DisplayItemAsyncHandler {
        // Create the packet
        val packet = PacketContainer(PacketType.Play.Server.SPAWN_ENTITY)
        // Set the entity ID
        packet.integers.write(0, itemID)
        // Set the entity UUID
        packet.uuiDs.write(0, itemUUID)
        // Set the entity type
        packet.entityTypeModifier.write(0, EntityType.ITEM_DISPLAY)

        // Set the location
        packet.doubles.write(0, loc.x)
        packet.doubles.write(1, loc.y)
        packet.doubles.write(2, loc.z)

        // Send the packet to the players
        sendPacket(packet)
        applyMetadata()
        processMovement()
        despawn(properties.maxLife)
        return this
    }

    private fun processMovement() {
        val initialPeriod = 10L // Initial period between updates
        val minPeriod = 5L // Minimum period between updates
        val maxPeriod = 20L // Maximum period between updates
        val speedThreshold = 0.5 // Threshold to increase update frequency

        object : BukkitRunnable() {
            var runTime = 0L
            var currentPeriod = initialPeriod

            override fun run() {
                if (runTime >= properties.maxLife) {
                    this.cancel()
                    return
                }

                // If there is no acceleration and damping, apply only velocity
                if (acceleration == Vector3(0.0, 0.0, 0.0) && damping == Vector3(0.0, 0.0, 0.0)) {
                    if (velocity != Vector3(0.0, 0.0, 0.0)) {
                        // Update position based on velocity only
                        loc.add(velocity.x, velocity.y, velocity.z)
                        setTeleportDuration(currentPeriod)
                        updatePosition()
                    }
                } else {
                    // Apply acceleration and damping
                    if (runTime < accelerationTicks) {
                        // Apply acceleration
                        velocity.x += acceleration.x / (20.0 / currentPeriod)
                        velocity.y += acceleration.y / (20.0 / currentPeriod)
                        velocity.z += acceleration.z / (20.0 / currentPeriod)
                    }

                    // Apply damping
                    velocity.x = (velocity.x * (1 - damping.x / (20.0 / currentPeriod))).coerceAtLeast(0.0)
                    velocity.y = (velocity.y * (1 - damping.y / (20.0 / currentPeriod))).coerceAtLeast(0.0)
                    velocity.z = (velocity.z * (1 - damping.z / (20.0 / currentPeriod))).coerceAtLeast(0.0)

                    // Update position with calculated speed factor
                    loc.add(velocity.x, velocity.y, velocity.z)
                    setTeleportDuration(currentPeriod)
                    updatePosition()
                }

                // Adjust update period based on velocity and distance
                val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z)
                val playerDistance = players.map { it.location.distance(loc) }.minOrNull() ?: 0.0
                currentPeriod = when {
                    speed > speedThreshold -> minPeriod
                    playerDistance < 10 -> initialPeriod
                    playerDistance < 50 -> initialPeriod * 2
                    else -> maxPeriod
                }

                runTime += currentPeriod
            }
        }.runTaskTimerAsynchronously(Defcon.instance, 0, initialPeriod)
    }

    private fun setTeleportDuration(period: Long) {
        // Use teleportDuration to ensure smooth interpolation
        val teleportDuration = period * 2 // Adjust as necessary
        val packedItems = mutableListOf(
        WrappedDataValue(
            10,
            WrappedDataWatcher.Registry.get(Int::class.javaObjectType),
            teleportDuration.toInt()
        ))
        sendMetadata(packedItems)
    }

    private fun updatePosition(): DisplayItemAsyncHandler {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT)
        packet.integers.write(0, itemID)
        packet.doubles.write(0, loc.x)
        packet.doubles.write(1, loc.y)
        packet.doubles.write(2, loc.z)
        packet.bytes.write(0, 0.toByte())
        packet.bytes.write(1, 0.toByte())
        packet.booleans.write(0, false)



        sendPacket(packet)
        return this
    }



    private fun despawn(afterTicks: Long): DisplayItemAsyncHandler {
        Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
            val packet = PacketContainer(PacketType.Play.Server.ENTITY_DESTROY)
            packet.intLists.write(0, listOf(itemID))
            sendPacket(packet)
        }, afterTicks)

        return this
    }

    private fun applyMetadata(): DisplayItemAsyncHandler {
        val brightnessValue = (properties.brightness.blockLight shl 4) or (properties.brightness.skyLight shl 20)
        val packedItems = mutableListOf(
            WrappedDataValue(
                8,
                cachedSerializers.get(RegistryName.Int),
                properties.interpolationDelay
            ),
            WrappedDataValue(
                9,
                cachedSerializers.get(RegistryName.Int),
                properties.interpolationDuration
            ),
            WrappedDataValue(
                10,
                cachedSerializers.get(RegistryName.Int),
                properties.teleportDuration
            ),
            WrappedDataValue(11, cachedSerializers.get(RegistryName.Vector3f), properties.translation),
            WrappedDataValue(12, cachedSerializers.get(RegistryName.Vector3f), properties.scale),
            WrappedDataValue(13, cachedSerializers.get(RegistryName.Quatenionf), properties.rotationLeft),
            WrappedDataValue(14, cachedSerializers.get(RegistryName.Quatenionf), properties.rotationRight),
            WrappedDataValue(
                15,
                cachedSerializers.get(RegistryName.Byte),
                properties.billboard.ordinal.toByte()
            ),
            WrappedDataValue(16, cachedSerializers.get(RegistryName.Int), brightnessValue),
            WrappedDataValue(17, cachedSerializers.get(RegistryName.Float), properties.viewRange),
            WrappedDataValue(18, cachedSerializers.get(RegistryName.Float), properties.shadowRadius),
            WrappedDataValue(
                19,
                cachedSerializers.get(RegistryName.Float),
                properties.shadowStrength
            ),
            WrappedDataValue(20, cachedSerializers.get(RegistryName.Float), properties.width),
            WrappedDataValue(21, cachedSerializers.get(RegistryName.Float), properties.height),

            WrappedDataValue(
                23,
                cachedSerializers.get(RegistryName.ItemStack),
                MinecraftReflection.getMinecraftItemStack(getItem())
            ),
        )
        sendMetadata(packedItems, true)
        return this
    }

    private fun getItem(): ItemStack {
        val itemStack = properties.itemStack.clone()
        val itemStackMeta = itemStack.itemMeta

        if (this.properties.color != null && itemStackMeta is LeatherArmorMeta) {
            itemStackMeta.setColor(this.properties.color)
        }

        if (this.properties.modelData != null) {
            itemStackMeta.setCustomModelData(this.properties.modelData)
        }

        itemStack.itemMeta = itemStackMeta
        return itemStack
    }

    private fun sendMetadata(wrappedDataValues: List<WrappedDataValue>, writeDefaults : Boolean = false) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
        packet.modifier.writeDefaults()
        packet.integers.write(0, itemID)

        packet.dataValueCollectionModifier.write(0, wrappedDataValues)

        sendPacket(packet)
    }

    private fun sendPacket(packet: PacketContainer) {
        try {
            players.forEach { player ->
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}