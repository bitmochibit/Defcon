/*
 * DEFCON: Nuclear warfare plugin for Minecraft servers.
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

package com.mochibit.defcon.particles.templates

import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.inventory.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Base class for defining generic particle properties.
 *
 * @property maxLife The maximum lifespan of the particle in ticks.
 * @property color The color of the particle.
 * @property scale The scale of the particle.
 */
abstract class GenericParticleProperties(
    var maxLife: Long = 300,
    var color: Color? = null,
    var scale: Vector3f = Vector3f(1.0f, 1.0f, 1.0f)
) : Cloneable {

    init {
        require(maxLife > 0) { "maxLife must be greater than 0." }
    }

    public override fun clone(): GenericParticleProperties {
        return super.clone() as GenericParticleProperties
    }
}

/**
 * Represents the properties of a display particle.
 *
 * @property itemStack The item stack associated with the particle.
 * @property interpolationDelay The delay before interpolation begins (in ticks).
 * @property interpolationDuration The duration of the interpolation (in ticks).
 * @property teleportDuration The duration of the teleport (in ticks).
 * @property translation The translation vector of the particle.
 * @property rotationLeft The left rotation quaternion of the particle.
 * @property rotationRight The right rotation quaternion of the particle.
 * @property billboard The billboard type of the particle.
 * @property brightness The brightness level of the particle.
 * @property viewRange The view range of the particle.
 * @property shadowRadius The radius of the particle's shadow.
 * @property shadowStrength The strength of the particle's shadow.
 * @property width The width of the particle.
 * @property height The height of the particle.
 * @property persistent If true, the particle persists after unloading.
 * @property modelData Custom model data for the particle.
 * @property modelDataAnimation The animation properties for the particle's model data.
 */
data class DisplayParticleProperties(
    var itemStack: ItemStack,

    var interpolationDelay: Int = 0,
    var interpolationDuration: Int = 0,
    var teleportDuration: Int = 1,

    var translation: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
    var rotationLeft: Quaternionf = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f),
    var rotationRight: Quaternionf = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f),

    var billboard: Display.Billboard = Display.Billboard.CENTER,
    var brightness: Display.Brightness = Display.Brightness(15, 15),

    var viewRange: Float = 100.0f,
    var shadowRadius: Float = 0.0f,
    var shadowStrength: Float = 0.0f,

    var width: Float = 0.0f,
    var height: Float = 0.0f,

    var persistent: Boolean = false,

    var modelData: Int? = null,
    var modelDataAnimation: ModelDataAnimation? = null
) : GenericParticleProperties() {

    init {
        require(viewRange > 0) { "viewRange must be greater than 0." }
        require(width >= 0) { "width cannot be negative." }
        require(height >= 0) { "height cannot be negative." }
    }

    override fun clone(): DisplayParticleProperties {
        return super.clone() as DisplayParticleProperties
    }

    /**
     * Defines the properties for animating model data.
     *
     * @property frameRate The frame rate of the animation in frames per second.
     * @property frames The sequence of texture indices used in the animation.
     */
    data class ModelDataAnimation(
        var frameRate: Int = 1,
        var frames: Array<Int> = emptyArray()
    ) {
        init {
            require(frameRate > 0) { "frameRate must be greater than 0." }
            require(frames.isNotEmpty()) { "frames array cannot be empty." }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ModelDataAnimation

            if (frameRate != other.frameRate) return false
            if (!frames.contentEquals(other.frames)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = frameRate
            result = 31 * result + frames.contentHashCode()
            return result
        }
    }
}
