package com.mochibit.defcon.customassets.customsounds

abstract class AbstractCustomSound (
    val soundData: SoundData
)
{
    val soundInfo: SoundInfo = this::class.java.getAnnotation(SoundInfo::class.java)
}