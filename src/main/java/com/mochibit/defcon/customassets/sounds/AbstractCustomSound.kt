package com.mochibit.defcon.customassets.sounds

abstract class AbstractCustomSound (
    val soundData: SoundData
)
{
    val soundInfo: SoundInfo = this::class.java.getAnnotation(SoundInfo::class.java)
}