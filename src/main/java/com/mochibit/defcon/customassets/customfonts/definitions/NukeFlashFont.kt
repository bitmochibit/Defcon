package com.mochibit.defcon.customassets.customfonts.definitions

import com.mochibit.defcon.customassets.customfonts.AbstractCustomFont
import com.mochibit.defcon.customassets.customfonts.FontData

class NukeFlashFont : AbstractCustomFont(
    FontData(
        type = "bitmap",
        file = "minecraft:nuke/nuke_flash.png",
        ascent = 128,
        height = 256,
        chars = hashSetOf("\uE000")
    )
) {}