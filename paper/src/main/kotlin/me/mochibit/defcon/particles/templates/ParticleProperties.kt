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

package me.mochibit.defcon.particles.templates

import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay.TextAlignment
import org.bukkit.inventory.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f

/**
 * Base class for defining generic particle properties.
 *
 * @property maxLife The maximum lifespan of the particle in ticks.
 * @property defaultColor The color of the particle.
 * @property scale The scale of the particle.
 */
data class ParticleTemplateProperties(
    var maxLife: Long = 60,
    var defaultColor: Color = Color.WHITE,
    val colorSettings: ColorSettings = ColorSettings(),
    val displayProperties: DisplayProperties = DisplayProperties(),
    val itemMode: ItemMode? = null,
    val textMode: TextMode? = null,

    val initialVelocity: Vector3d = Vector3d(0.0, 0.0, 0.0),
    val initialAcceleration: Vector3d = Vector3d(0.0, 0.0, 0.0),
    val initialDampening: Vector3d = Vector3d(0.0, 0.0, 0.0),
    /**
     * Position displacement, applied randomly based on the vector
     */
    val displacement: Vector3d = Vector3d(0.0, 0.0, 0.0),
) {
    data class ColorSettings(
        var randomizeColorBrightness: Boolean = true,
        var maxLightenFactor: Double = .2,
        var minLightenFactor: Double = 0.0,
        var maxDarkenFactor: Double = 1.0,
        var minDarkenFactor: Double = 0.8,
    )
}

data class DisplayProperties(
    var interpolationDelay: Int = 0,
    var interpolationDuration: Int = 0,
    var teleportDuration: Int = 1,
    val scale: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    val translation: Vector3f = Vector3f(0.0f, 0.0f, 0.0f),
    val rotationLeft: Quaternionf = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f),
    val rotationRight: Quaternionf = Quaternionf(0.0f, 0.0f, 0.0f, 1.0f),
    var billboard: Display.Billboard = Display.Billboard.CENTER,
    var brightness: Display.Brightness = Display.Brightness(15, 15),
    var viewRange: Float = 100.0f,
    var shadowRadius: Float = 0.0f,
    var shadowStrength: Float = 0.0f,
    var width: Float = 0.0f,
    var height: Float = 0.0f,
    var persistent: Boolean = false,
)

data class ItemMode(
    var itemStack: ItemStack,
    @Deprecated("This is only for legacy support", ReplaceWith("modelName"), DeprecationLevel.WARNING)
    var modelData: Int? = null,
    var modelName: String? = null,
)

data class TextMode(
    var text: String,
    var color: Color = Color.WHITE,
    var backgroundColor: Color = Color.fromARGB(0, 0, 0, 0),
    var textOpacity: Byte = -1,
    var hasShadow: Boolean = false,
    var isSeeThrough: Boolean = false,
    var useDefaultBackground: Boolean = false,
    var alignment: TextAlignment = TextAlignment.CENTER,
    var lineWidth: Int = 200,
)