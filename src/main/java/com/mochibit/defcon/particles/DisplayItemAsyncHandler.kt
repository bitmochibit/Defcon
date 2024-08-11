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
    val properties: DisplayParticleProperties
) {
    companion object {
        private const val MAX_PARTICLES_PER_PLAYER = 800
        private const val DEFAULT_DAMPING_FACTOR = 0.1
        private const val SPEED_THRESHOLD = 0.5
        private const val INITIAL_PERIOD = 10L
        private const val MIN_PERIOD = 5L
        private const val MAX_PERIOD = 20L

        private val playerParticleCount = mutableMapOf<UUID, Int>()
        val cachedSerializers = mutableMapOf<RegistryName, WrappedDataWatcher.Serializer>(
            RegistryName.Vector3f to WrappedDataWatcher.Registry.get(Vector3f::class.java),
            RegistryName.Quatenionf to WrappedDataWatcher.Registry.get(Quaternionf::class.java),
            RegistryName.Byte to WrappedDataWatcher.Registry.get(Byte::class.javaObjectType),
            RegistryName.Int to WrappedDataWatcher.Registry.get(Int::class.javaObjectType),
            RegistryName.Float to WrappedDataWatcher.Registry.get(Float::class.javaObjectType),
            RegistryName.ItemStack to WrappedDataWatcher.Registry.getItemStackSerializer(false)
        )
    }

    val itemID = (Math.random() * Int.MAX_VALUE).toInt()
    val itemUUID = UUID.randomUUID()
    var velocity = Vector3(0.0, 0.0, 0.0); private set
    var damping = Vector3(DEFAULT_DAMPING_FACTOR, DEFAULT_DAMPING_FACTOR, DEFAULT_DAMPING_FACTOR); private set
    var acceleration = Vector3(0.0, 0.0, 0.0); private set
    var accelerationTicks = 0; private set

    fun initialVelocity(initialVelocity: Vector3) = apply { this.velocity = initialVelocity.clone() }
    fun damping(damping: Vector3) = apply { this.damping = damping.clone() }
    fun acceleration(acceleration: Vector3) = apply { this.acceleration = acceleration.clone() }
    fun accelerationTicks(accelerationTicks: Int) = apply { this.accelerationTicks = accelerationTicks }

    fun summon(): DisplayItemAsyncHandler {
        val packet = createSpawnPacket()
        getPlayers().forEach {
            if (canSpawnParticle(it)) {
                sendPacket(packet, it)
                incrementParticleCount(it)
            }
        }
        applyMetadata()
        processMovement()
        scheduleDespawn(properties.maxLife)
        return this
    }

    private fun createSpawnPacket(): PacketContainer {
        val packet = PacketContainer(PacketType.Play.Server.SPAWN_ENTITY)
        packet.integers.write(0, itemID)
        packet.uuiDs.write(0, itemUUID)
        packet.entityTypeModifier.write(0, EntityType.ITEM_DISPLAY)
        packet.doubles.write(0, loc.x)
        packet.doubles.write(1, loc.y)
        packet.doubles.write(2, loc.z)
        return packet
    }

    private fun canSpawnParticle(player: Player): Boolean {
        return playerParticleCount.getOrDefault(player.uniqueId, 0) < MAX_PARTICLES_PER_PLAYER
    }

    private fun incrementParticleCount(player: Player) {
        playerParticleCount[player.uniqueId] = playerParticleCount.getOrDefault(player.uniqueId, 0) + 1
    }

    private fun processMovement() {
        object : BukkitRunnable() {
            var runTime = 0L
            var currentPeriod = INITIAL_PERIOD

            override fun run() {
                if (runTime >= properties.maxLife) {
                    this.cancel()
                    return
                }

                updateVelocityAndPosition(currentPeriod)
                adjustUpdatePeriod()
                runTime += currentPeriod
            }

            private fun updateVelocityAndPosition(period: Long) {
                val timeFactor = 20.0 / period

                if (runTime < accelerationTicks) {
                    velocity = velocity + acceleration.div(timeFactor)
                }

                velocity = velocity - damping.div(timeFactor)
                loc.add(velocity.toBukkitVector())
                setTeleportDuration(period)
                updatePosition()
            }

            private fun adjustUpdatePeriod() {
                val speed = velocity.length()
                currentPeriod = if (speed > SPEED_THRESHOLD) MIN_PERIOD else MAX_PERIOD
            }
        }.runTaskTimerAsynchronously(Defcon.instance, 0, INITIAL_PERIOD)
    }

    private fun setTeleportDuration(period: Long) {
        val teleportDuration = period * 2
        sendMetadata(
            listOf(
                WrappedDataValue(
                    10,
                    cachedSerializers.get(RegistryName.Int),
                    teleportDuration.toInt()
                )
            )
        )
    }

    private fun updatePosition(): DisplayItemAsyncHandler {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT)
        packet.integers.write(0, itemID)
        packet.doubles.write(0, loc.x)
        packet.doubles.write(1, loc.y)
        packet.doubles.write(2, loc.z)
        getPlayers().forEach { sendPacket(packet, it) }
        return this
    }

    private fun scheduleDespawn(afterTicks: Long): DisplayItemAsyncHandler {
        Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
            val packet = PacketContainer(PacketType.Play.Server.ENTITY_DESTROY)
            packet.intLists.write(0, listOf(itemID))
            getPlayers(true).forEach {
                decrementParticleCount(it)
                sendPacket(packet, it)
            }
        }, afterTicks)
        return this
    }

    private fun decrementParticleCount(player: Player) {
        playerParticleCount[player.uniqueId] = (playerParticleCount.getOrDefault(player.uniqueId, 0) - 1).coerceAtLeast(0)
    }

    private fun applyMetadata(): DisplayItemAsyncHandler {
        val brightnessValue = (properties.brightness.blockLight shl 4) or (properties.brightness.skyLight shl 20)
        val packedItems = listOf(
            WrappedDataValue(8, cachedSerializers.get(RegistryName.Int), properties.interpolationDelay),
            WrappedDataValue(9, cachedSerializers.get(RegistryName.Int), properties.interpolationDuration),
            WrappedDataValue(10, cachedSerializers.get(RegistryName.Int), properties.teleportDuration),
            WrappedDataValue(11, cachedSerializers.get(RegistryName.Vector3f), properties.translation),
            WrappedDataValue(12, cachedSerializers.get(RegistryName.Vector3f), properties.scale),
            WrappedDataValue(13, cachedSerializers.get(RegistryName.Quatenionf), properties.rotationLeft),
            WrappedDataValue(14, cachedSerializers.get(RegistryName.Quatenionf), properties.rotationRight),
            WrappedDataValue(15, cachedSerializers.get(RegistryName.Byte), properties.billboard.ordinal.toByte()),
            WrappedDataValue(16, cachedSerializers.get(RegistryName.Int), brightnessValue),
            WrappedDataValue(17, cachedSerializers.get(RegistryName.Float), properties.viewRange),
            WrappedDataValue(18, cachedSerializers.get(RegistryName.Float), properties.shadowRadius),
            WrappedDataValue(19, cachedSerializers.get(RegistryName.Float), properties.shadowStrength),
            WrappedDataValue(20, cachedSerializers.get(RegistryName.Float), properties.width),
            WrappedDataValue(21, cachedSerializers.get(RegistryName.Float), properties.height),
            WrappedDataValue(23, cachedSerializers.get(RegistryName.ItemStack), MinecraftReflection.getMinecraftItemStack(getItem())),
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
    private fun getPlayers(getAll: Boolean = false): Collection<Player> {
        val players = Bukkit.getOnlinePlayers()
        return if (getAll) players else players.filter { it.location.distanceSquared(loc) < properties.viewRange * properties.viewRange }
    }

    private fun sendMetadata(wrappedDataValues: List<WrappedDataValue>, writeDefaults : Boolean = false) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
        packet.modifier.writeDefaults()
        packet.integers.write(0, itemID)

        packet.dataValueCollectionModifier.write(0, wrappedDataValues)

        getPlayers().forEach { sendPacket(packet, it) }
    }

    private fun sendPacket(packet: PacketContainer, player: Player) {
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
    }
}
