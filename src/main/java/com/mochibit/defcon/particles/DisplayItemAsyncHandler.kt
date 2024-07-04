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
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject
import com.mochibit.defcon.Defcon
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID


class DisplayItemAsyncHandler(val loc: Location, val players: MutableCollection<out Player>, val properties: DisplayParticleProperties) {
    /**
     * The random ID of the display item. It is a Variable-length data encoding a two's complement signed 32-bit integer;
     */
    val itemID = (Math.random() * Int.MAX_VALUE).toInt()
    val itemUUID = UUID.randomUUID()

    /**
     * Sends the packet to the players inside the list, to display the item
     */
    fun summon() {
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
        despawn(properties.maxLife)
    }

    fun despawn(afterTicks: Long) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
            val packet = PacketContainer(PacketType.Play.Server.ENTITY_DESTROY)
            packet.intLists.write(0, listOf(itemID))
            sendPacket(packet)
        }, afterTicks)
    }

    fun summonWithMetadata() {
        summon()
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
        packet.modifier.writeDefaults()
        packet.integers.write(0, itemID)


        val brightnessValue = (properties.brightness.blockLight shl 4) or (properties.brightness.skyLight shl 20)
        val packedItems = mutableListOf(
            WrappedDataValue(8, WrappedDataWatcher.Registry.get(Int::class.javaObjectType), properties.interpolationDelay),
            WrappedDataValue(9, WrappedDataWatcher.Registry.get(Int::class.javaObjectType), properties.interpolationDuration),
            WrappedDataValue(10, WrappedDataWatcher.Registry.get(Int::class.javaObjectType), properties.teleportDuration),
            WrappedDataValue(11, WrappedDataWatcher.Registry.get(Vector3f::class.java), properties.translation),
            WrappedDataValue(12, WrappedDataWatcher.Registry.get(Vector3f::class.java), properties.scale),
            WrappedDataValue(13, WrappedDataWatcher.Registry.get(Quaternionf::class.java), properties.rotationLeft),
            WrappedDataValue(14, WrappedDataWatcher.Registry.get(Quaternionf::class.java), properties.rotationRight),
            WrappedDataValue(15, WrappedDataWatcher.Registry.get(Byte::class.javaObjectType), properties.billboard.ordinal.toByte()),
            WrappedDataValue(16, WrappedDataWatcher.Registry.get(Int::class.javaObjectType), brightnessValue),
            WrappedDataValue(17, WrappedDataWatcher.Registry.get(Float::class.javaObjectType), properties.viewRange),
            WrappedDataValue(18, WrappedDataWatcher.Registry.get(Float::class.javaObjectType), properties.shadowRadius),
            WrappedDataValue(19, WrappedDataWatcher.Registry.get(Float::class.javaObjectType), properties.shadowStrength),
            WrappedDataValue(20, WrappedDataWatcher.Registry.get(Float::class.javaObjectType), properties.width),
            WrappedDataValue(21, WrappedDataWatcher.Registry.get(Float::class.javaObjectType), properties.height),

            WrappedDataValue(23, WrappedDataWatcher.Registry.getItemStackSerializer(false), MinecraftReflection.getMinecraftItemStack(getItem())),
        )

        packet.dataValueCollectionModifier.write(0, packedItems)
        sendPacket(packet)
    }

    private fun getItem() : ItemStack {
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