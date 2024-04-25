package com.mochibit.defcon.customassets.sounds

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SoundInfo(
    val directory : String,
    val name : String,
)
