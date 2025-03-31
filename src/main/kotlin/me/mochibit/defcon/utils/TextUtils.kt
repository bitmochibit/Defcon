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

package me.mochibit.defcon.utils

import me.mochibit.defcon.utils.MathFunctions.lerp
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.*
import net.kyori.adventure.text.format.TextColor.color
import net.kyori.adventure.text.format.TextColor.fromHexString

fun getComponentWithGradient(
    text: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strikethrough: Boolean = false,
    colors: List<String> = listOf("#FF0000", "#00FF00", "#0000FF"),
): net.kyori.adventure.text.Component {
    if (text.isEmpty()) return empty()
    if (colors.isEmpty()) return text(text)

    // Convert hex colors to TextColor objects and filter out nulls
    val textColors = colors.mapNotNull { fromHexString(it) }

    // If we don't have at least two colors after filtering, use the text as is
    if (textColors.size < 2) {
        return text(text)
            .decorations(createDecorationMap(bold, italic, underline, strikethrough))
            .color(if (textColors.isNotEmpty()) textColors[0] else null)
    }

    // Create gradient steps between letters
    val totalChars = text.length - 1
    val colorSegments = textColors.size - 1

    // Main component to build
    var component = empty()

    // For each character, calculate its color in the gradient
    text.forEachIndexed { index, char ->
        // Calculate position in the gradient (0.0 to 1.0)
        val position = if (totalChars > 0) index.toFloat() / totalChars else 0f

        // Calculate which color segment this character belongs to
        val segmentIndex = (position * colorSegments).toInt().coerceAtMost(colorSegments - 1)
        val segmentPosition = (position * colorSegments) - segmentIndex.toDouble()

        // Get the two colors for interpolation
        val color1 = textColors[segmentIndex]
        val color2 = textColors[segmentIndex + 1]

        // Interpolate between the two colors
        val r = lerp(color1.red(), color2.red(), segmentPosition)
        val g = lerp(color1.green(), color2.green(), segmentPosition)
        val b = lerp(color1.blue(), color2.blue(), segmentPosition)

        // Create color from interpolated values
        val interpolatedColor = color(r, g, b)

        // Create styled component for this character
        val charComponent = text(char.toString())
            .color(interpolatedColor)
            .decorations(createDecorationMap(bold, italic, underline, strikethrough))

        // Append to main component
        component = component.append(charComponent)
    }

    return component
}


private fun createDecorationMap(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strikethrough: Boolean
): Map<TextDecoration, TextDecoration.State> {
    val decorations = mutableMapOf<TextDecoration, TextDecoration.State>()

    if (bold) decorations[TextDecoration.BOLD] = TextDecoration.State.TRUE
    if (italic) decorations[TextDecoration.ITALIC] = TextDecoration.State.TRUE
    if (underline) decorations[TextDecoration.UNDERLINED] = TextDecoration.State.TRUE
    if (strikethrough) decorations[TextDecoration.STRIKETHROUGH] = TextDecoration.State.TRUE

    return decorations
}
