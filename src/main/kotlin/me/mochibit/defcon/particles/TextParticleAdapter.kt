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
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.AdventureComponentConverter
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedDataValue
import me.mochibit.defcon.extensions.toInt
import me.mochibit.defcon.particles.templates.TextDisplayParticleProperties
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.joml.Vector3f
import java.util.*

class TextParticleAdapter(properties: TextDisplayParticleProperties) : DisplayEntityParticleAdapter<TextDisplayParticleProperties>(properties) {
    private val wrappedChatComponentCache = mutableMapOf<Component, WrappedChatComponent>()
    private val textComponentCache = mutableMapOf<TextColor, Component>()

    override fun getSpawnPacket(displayID: Int, displayUUID: UUID, location: Vector3f): PacketContainer {
        val packet = PacketContainer(PacketType.Play.Server.SPAWN_ENTITY)
        packet.integers.write(0, displayID)
        packet.uuiDs.write(0, displayUUID)
        packet.entityTypeModifier.write(0, EntityType.TEXT_DISPLAY)
        with(packet.doubles) {
            write(0, location.x.toDouble())
            write(1, location.y.toDouble())
            write(2, location.z.toDouble())
        }
        return packet
    }


    override fun getSubMeta(displayID: Int, particleProperties: TextDisplayParticleProperties, players: List<Player>): List<WrappedDataValue> {
        val color = TextColor.color((particleProperties.color ?: Color.BLACK).asRGB())

        val textComponent : Component = textComponentCache.computeIfAbsent(color) {
            text().content(particleProperties.text).color(color).build()
        }

        val effectiveText = wrappedChatComponentCache.computeIfAbsent(textComponent) {
            AdventureComponentConverter.fromComponent(it)
        }

        // 0x01 (Has shadow), 0x02 (Is see through), 0x04 (Use default background color), 0x08 (Alignment, 0 CENTER, 1 or 3 LEFT, 2 RIGHT)
        val textPropertiesByteMask = particleProperties.hasShadow.toInt() or (particleProperties.isSeeThrough.toInt() shl 1) or (particleProperties.useDefaultBackground.toInt() shl 2) or (particleProperties.alignment.ordinal shl 3)
        val packedItems = listOf(
            WrappedDataValue(23, cachedSerializers[RegistryName.TextComponent], effectiveText.handle),
            WrappedDataValue(24, cachedSerializers[RegistryName.Int], particleProperties.lineWidth),
            WrappedDataValue(25, cachedSerializers[RegistryName.Int], particleProperties.backgroundColor.asRGB()),
            WrappedDataValue(26, cachedSerializers[RegistryName.Byte], particleProperties.textOpacity),
            WrappedDataValue(27, cachedSerializers[RegistryName.Byte], textPropertiesByteMask.toByte()),
        )
        return packedItems
    }
}