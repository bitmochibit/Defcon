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

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityType
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import me.mochibit.defcon.extensions.toInt
import me.mochibit.defcon.particles.templates.TextDisplayParticleProperties
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.joml.Vector3d
import org.joml.Vector3f

/**
 * Text display particle implementation
 * Optimized for better performance with large particle counts
 */
class TextDisplayParticleInstance(
    particleProperties: TextDisplayParticleProperties,
    position: Vector3d = Vector3d(0.0, 0.0, 0.0),
    velocity: Vector3d = Vector3d(0.0, 0.0, 0.0),
    damping: Vector3d = Vector3d(0.0, 0.0, 0.0),
    acceleration: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
) : ClientSideParticleInstance(particleProperties, position, velocity, damping, acceleration) {

    // Cache text component as it doesn't change
    private val textComponent by lazy {
        val color = TextColor.color((particleProperties.color ?: Color.BLACK).asRGB())
        Component.text(particleProperties.text).color(color)
    }

    // Cache metadata list
    private val textMetadataList: List<EntityData> by lazy {
        val textFlags = (particleProperties.hasShadow.toInt() or
                (particleProperties.isSeeThrough.toInt() shl 1) or
                (particleProperties.useDefaultBackground.toInt() shl 2) or
                (particleProperties.alignment.ordinal shl 3)).toByte()

        listOf(
            EntityData(23, EntityDataTypes.ADV_COMPONENT, textComponent),
            EntityData(24, EntityDataTypes.INT, particleProperties.lineWidth),
            EntityData(25, EntityDataTypes.INT, particleProperties.backgroundColor.asRGB()),
            EntityData(26, EntityDataTypes.BYTE, particleProperties.textOpacity),
            EntityData(27, EntityDataTypes.BYTE, textFlags)
        )
    }

    override fun getMetadataList(): List<EntityData> = textMetadataList

    override fun getEntityType(): EntityType = EntityTypes.TEXT_DISPLAY
}