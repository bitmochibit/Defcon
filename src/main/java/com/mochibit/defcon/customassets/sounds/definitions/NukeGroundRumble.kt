package com.mochibit.defcon.customassets.sounds.definitions

import com.mochibit.defcon.customassets.sounds.AbstractCustomSound
import com.mochibit.defcon.customassets.sounds.SoundData
import com.mochibit.defcon.customassets.sounds.SoundInfo

@SoundInfo("nuke", "ground_rumble")
class NukeGroundRumble(): AbstractCustomSound(
    SoundData(
        sounds = hashSetOf("nuke/ground_rumble")
    )
){}
