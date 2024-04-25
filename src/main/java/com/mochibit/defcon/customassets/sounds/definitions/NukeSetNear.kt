package com.mochibit.defcon.customassets.sounds.definitions

import com.mochibit.defcon.customassets.sounds.AbstractCustomSound
import com.mochibit.defcon.customassets.sounds.SoundData
import com.mochibit.defcon.customassets.sounds.SoundInfo

@SoundInfo("nuke", "set_near")
class NukeSetNear(): AbstractCustomSound(
    SoundData(
        sounds = hashSetOf("nuke/set_near")
    )
){}
