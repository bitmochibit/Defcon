package com.mochibit.defcon.customassets.fonts.definitions

import com.mochibit.defcon.customassets.fonts.AbstractCustomFont
import com.mochibit.defcon.customassets.fonts.FontData

class NukeFlashFont : AbstractCustomFont(
    FontData(
        type = "bitmap",
        file = "minecraft:nuke/nuke_flash.png",
        ascent = 128,
        height = 256,
        chars = hashSetOf("\uE000")
    )
) {}