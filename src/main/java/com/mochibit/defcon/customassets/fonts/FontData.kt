package com.mochibit.defcon.customassets.fonts

data class FontData(
    val type: String = "bitmap",
    val file : String = "minecraft:defcon/default.png",
    val ascent : Int = 7,
    val height : Int = 9,
    // Hashset of characters which uses the font
    val chars : HashSet<String> = hashSetOf(),
    // Array of advances for each character
    val advances : HashMap<String, Double> = hashMapOf()
)
