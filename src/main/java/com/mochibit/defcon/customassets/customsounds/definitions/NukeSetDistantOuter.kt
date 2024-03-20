package com.mochibit.defcon.customassets.customsounds.definitions

import com.mochibit.defcon.customassets.customsounds.AbstractCustomSound
import com.mochibit.defcon.customassets.customsounds.SoundData
import com.mochibit.defcon.customassets.customsounds.SoundInfo

@SoundInfo("nuke", "set_distant_outer")
class NukeSetDistantOuter(): AbstractCustomSound(
    SoundData(
        sounds = hashSetOf("nuke/set_distant_outer")
    )
){}
