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

package com.mochibit.defcon.utils

import com.mochibit.defcon.utils.MathFunctions.lerp
import net.md_5.bungee.api.ChatColor
import org.bukkit.Color
import java.util.regex.Pattern
import kotlin.math.ln
import kotlin.math.pow

object ColorUtils {
    private val rgbPattern = Pattern.compile("&#[a-fA-F0-9]{6}")

    /**
     * @param inputString
     * This function gets a base string and tries to parse any color code correctly.
     * This method supports RGB too and base color.
     * @return String
     */
    fun parseColor(inputString: String): String {
        // Try to parse RGB colors
        var match = rgbPattern.matcher(inputString)
        // Test &#cbfb09G&#cff408e&#d3ed08i&#d8e607g&#dcdf06e&#e0d805r

        var parsedString = inputString

        while (match.find()) {
            val color = parsedString.substring(match.start(), match.end()) //Color: &#cbfb09G
            parsedString = parsedString.replace(color, ChatColor.of(color.replace("&", "")).toString() + "")
            match = rgbPattern.matcher(parsedString)
        }

        // Parse basic color codes
        parsedString = ChatColor.translateAlternateColorCodes('&', parsedString)
        return parsedString
    }

    fun parseColor(inputStrings: MutableList<String>): List<String> {
        val i = inputStrings.listIterator()
        while (i.hasNext()) {
            val element = i.next()
            i.set(parseColor(element))
        }
        return inputStrings
    }

    fun stripColor(inputString: String): String {
        // Strip any color codes from the string
        return ChatColor.stripColor(inputString)
    }

    fun tempToRGB(temperature: Double): Color {
        val r: Int
        val g: Int
        val b: Int

        val temp = temperature / 100

        // Red
        r = if (temp <= 66) {
            255
        } else {
            val red = 329.698727446 * (temp - 60).pow(-0.1332047592)
            red.coerceIn(0.0, 255.0).toInt()
        }

        // Green
        g = if (temp <= 66) {
            val green = 99.4708025861 * ln(temp) - 161.1195681661
            green.coerceIn(0.0, 255.0).toInt()
        } else {
            val green = 288.1221695283 * (temp - 60).pow(-0.0755148492)
            green.coerceIn(0.0, 255.0).toInt()
        }

        // Blue
        b = if (temp >= 66) {
            255
        } else if (temp <= 19) {
            0
        } else {
            val blue = 138.5177312231 * ln(temp - 10) - 305.0447927307
            blue.coerceIn(0.0, 255.0).toInt()
        }

        return Color.fromRGB(r, g, b)
    }

    fun lerpColor(a: Color, b: Color, t: Double): Color {
        val r = lerp(a.red.toDouble(), b.red.toDouble(), t)
        val g = lerp(a.green.toDouble(), b.green.toDouble(), t)
        val bl = lerp(a.blue.toDouble(), b.blue.toDouble(), t)
        return Color.fromRGB(r.toInt(), g.toInt(), bl.toInt())
    }

    fun darkenColor(color: Color, factor: Double): Color {
        val r = (color.red * factor).toInt()
        val g = (color.green * factor).toInt()
        val b = (color.blue * factor).toInt()
        return Color.fromRGB(r, g, b)
    }

    fun lightenColor(color: Color, factor: Double): Color {
        val r = (color.red + (255 - color.red) * factor).toInt()
        val g = (color.green + (255 - color.green) * factor).toInt()
        val b = (color.blue + (255 - color.blue) * factor).toInt()
        return Color.fromRGB(r, g, b)
    }

}
