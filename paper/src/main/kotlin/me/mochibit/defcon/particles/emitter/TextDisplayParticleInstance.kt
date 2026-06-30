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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.entity.TextDisplay
import org.joml.Vector3d
import org.joml.Vector3f

/**
 * Text display particle implementation
 * Optimized for better performance with large particle counts
 */

data class TextDisplayParticleProperties(
    val text: String,
    val lineWidth: Int = 0,
    val backgroundColor: Color = Color.BLACK,
    val textOpacity: Byte = 0,
    val hasShadow: Boolean = false,
    val isSeeThrough: Boolean = false,
    val useDefaultBackground: Boolean = false,
    val alignment: TextDisplay.TextAlignment = TextDisplay.TextAlignment.CENTER,
)

class TextDisplayParticleInstance(
    textDisplayProperties: TextDisplayParticleProperties,
    particleProperties: DisplayParticleProperties,
    maxLife: Long = 20,
    position: Vector3d = Vector3d(0.0, 0.0, 0.0),
    velocity: Vector3d = Vector3d(0.0, 0.0, 0.0),
    damping: Vector3d = Vector3d(0.0, 0.0, 0.0),
    acceleration: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
) : ClientSideParticleInstance(maxLife, particleProperties, position, velocity, damping, acceleration) {

    // Cache text component as it doesn't change
    private val textComponent by lazy {
        val textColor = TextColor.color(this.color.asRGB())
        Component.text(textDisplayProperties.text).color(textColor)
    }

    // Cache metadata list
    private val textMetadataList: List<EntityData> by lazy {
        val textFlags = (textDisplayProperties.hasShadow.toInt() or
                (textDisplayProperties.isSeeThrough.toInt() shl 1) or
                (textDisplayProperties.useDefaultBackground.toInt() shl 2) or
                (textDisplayProperties.alignment.ordinal shl 3)).toByte()

        listOf(
            EntityData(23, EntityDataTypes.ADV_COMPONENT, textComponent),
            EntityData(24, EntityDataTypes.INT, textDisplayProperties.lineWidth),
            EntityData(25, EntityDataTypes.INT, textDisplayProperties.backgroundColor.asRGB()),
            EntityData(26, EntityDataTypes.BYTE, textDisplayProperties.textOpacity),
            EntityData(27, EntityDataTypes.BYTE, textFlags)
        )
    }

    override fun getMetadataList(): List<EntityData> = textMetadataList

    override fun getEntityType(): EntityType = EntityTypes.TEXT_DISPLAY
}