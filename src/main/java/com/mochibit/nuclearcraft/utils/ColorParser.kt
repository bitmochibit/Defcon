package com.mochibit.nuclearcraft.utils

import net.md_5.bungee.api.ChatColor
import java.util.regex.Pattern

object ColorParser {
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
}
