package com.enderryno.nuclearcraft.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorParser {

    private static final Pattern rgbPattern = Pattern.compile("&#[a-fA-F0-9]{6}");

    /**
     * @param inputString
     * This function gets a base string and tries to parse any color code correctly.
     * This method supports RGB too and base color.
     * @return String
     */
    public static String parseColor(String inputString) {
        // Try to parse RGB colors
        Matcher match = rgbPattern.matcher(inputString);
        while(match.find()) {
            String color = inputString.substring(match.start(), match.end());
            inputString = inputString.replace("&", "");
            inputString = inputString.replace(color, ChatColor.of(color) + "");
            match = rgbPattern.matcher(inputString);
        }

        // Parse basic color codes
        inputString = ChatColor.translateAlternateColorCodes('&', inputString);

        return inputString;
    }

    public static List<String> parseColor(List<String> inputStrings)
    {
        for(final ListIterator<String> i = inputStrings.listIterator(); i.hasNext();) {
            final String element = i.next();
            i.set(parseColor(element));
        }


        return inputStrings;
    }

}
