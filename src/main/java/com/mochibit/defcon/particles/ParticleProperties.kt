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

import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

abstract class GenericParticleProperties {
    var maxLife: Long = 100
    var color: Color? = null
}

/**
 * The properties of a particle.
 * @param itemStack The item stack of the particle.
 * @param maxLife The maximum life of the particle. (In ticks)
 */
data class DisplayParticleProperties(
    var itemStack: ItemStack,

    var interpolationDelay : Int = 0,
    var interpolationDuration: Int = 0,
    var teleportDuration: Int = 0,

    var translation : Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
    var scale: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    var rotationLeft: Quaternionf = Quaternionf(0.0, 0.0, 0.0, 1.0),
    var rotationRight: Quaternionf = Quaternionf(0.0, 0.0, 0.0, 1.0),

    var billboard: Display.Billboard = Display.Billboard.CENTER,
    var brightness: Display.Brightness = Display.Brightness(15, 15),

    var viewRange: Float = 100.0f,
    var shadowRadius: Float = 0.0f,
    var shadowStrength: Float = 0.0F,

    var width : Float = 0.0f,
    var height : Float = 0.0f,

    var persistent: Boolean = false,

    var modelData: Int? = null,
    var modelDataAnimation: ModelDataAnimation? = null
) : GenericParticleProperties() {
    /**
     * @param frameRate The frame rate of the animation. (Frames per second)
     * @param frames The frames of the animation. (The texture index of the frame)
     */
    data class ModelDataAnimation(
        var frameRate : Int = 1,
        var frames : Array<Int> = emptyArray()
    )
}


