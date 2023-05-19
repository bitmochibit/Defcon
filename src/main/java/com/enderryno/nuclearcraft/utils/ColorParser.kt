package com.enderryno.nuclearcraft.utils

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
    fun parseColor(inputString: String?): String? {
        // Try to parse RGB colors
        var inputString = inputString
        var match = rgbPattern.matcher(inputString)
        // Test &#cbfb09G&#cff408e&#d3ed08i&#d8e607g&#dcdf06e&#e0d805r
        while (match.find()) {
            val color = inputString!!.substring(match.start(), match.end()) //Color: &#cbfb09G
            inputString = inputString.replace(color, ChatColor.of(color.replace("&", "")).toString() + "")
            match = rgbPattern.matcher(inputString)
        }

        // Parse basic color codes
        inputString = ChatColor.translateAlternateColorCodes('&', inputString)
        return inputString
    }

    fun parseColor(inputStrings: MutableList<String?>): List<String?> {
        val i = inputStrings.listIterator()
        while (i.hasNext()) {
            val element = i.next()
            i.set(parseColor(element))
        }
        return inputStrings
    }
}
