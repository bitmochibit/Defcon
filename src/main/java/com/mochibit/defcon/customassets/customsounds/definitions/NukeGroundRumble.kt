package com.mochibit.defcon.customassets.customsounds.definitions

import com.mochibit.defcon.customassets.customsounds.AbstractCustomSound
import com.mochibit.defcon.customassets.customsounds.SoundData
import com.mochibit.defcon.customassets.customsounds.SoundInfo

@SoundInfo("nuke", "ground_rumble")
class NukeGroundRumble(): AbstractCustomSound(
    SoundData(
        sounds = hashSetOf("nuke/ground_rumble")
    )
){}
