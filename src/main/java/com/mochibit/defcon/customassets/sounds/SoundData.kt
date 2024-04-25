package com.mochibit.defcon.customassets.sounds

data class SoundData (
    // All the sounds string paths (if more than one they will be played randomly)
    val sounds: HashSet<String>,
    // In the future, we can add more parameters like volume, pitch, etc
)
