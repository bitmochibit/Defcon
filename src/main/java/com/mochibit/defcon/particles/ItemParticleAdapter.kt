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
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.utility.MinecraftReflection
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.templates.DisplayParticleProperties
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.joml.Vector3f
import java.util.*

object ItemParticleAdapter : DisplayEntityParticleAdapter() {
    override fun getSpawnPacket(displayID: Int, displayUUID: UUID, location: Vector3f): PacketContainer {
        val packet = PacketContainer(PacketType.Play.Server.SPAWN_ENTITY)
        packet.integers.write(0, displayID)
        packet.uuiDs.write(0, displayUUID)
        packet.entityTypeModifier.write(0, EntityType.ITEM_DISPLAY)
        packet.doubles.write(0, location.x.toDouble())
        packet.doubles.write(1, location.y.toDouble())
        packet.doubles.write(2, location.z.toDouble())
        return packet
    }

    override fun applyMetadata(displayID: Int, particleProperties: DisplayParticleProperties, players: List<Player>) {
        val brightnessValue =
            (particleProperties.brightness.blockLight shl 4) or (particleProperties.brightness.skyLight shl 20)
        val packedItems = listOf(
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
            WrappedDataValue(
                23,
                cachedSerializers[RegistryName.ItemStack],
                MinecraftReflection.getMinecraftItemStack(getItem(particleProperties))
            ),
        )
        sendMetadata(displayID, packedItems, players)

    }

    private fun getItem(particleProperties: DisplayParticleProperties): ItemStack {
        val itemStack = particleProperties.itemStack.clone()
        val itemStackMeta = itemStack.itemMeta

        if (particleProperties.color != null && itemStackMeta is LeatherArmorMeta) {
            itemStackMeta.setColor(particleProperties.color)
        }

        if (particleProperties.modelData != null) {
            itemStackMeta.setCustomModelData(particleProperties.modelData)
        }

        itemStack.itemMeta = itemStackMeta
        return itemStack
    }

}