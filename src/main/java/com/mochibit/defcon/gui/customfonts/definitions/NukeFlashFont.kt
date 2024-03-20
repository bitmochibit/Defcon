package com.mochibit.defcon.gui.customfonts.definitions

import com.mochibit.defcon.gui.customfonts.AbstractCustomFont
import com.mochibit.defcon.gui.customfonts.CustomFontData

class NukeFlashFont : AbstractCustomFont(
    CustomFontData(
        type = "bitmap",
        file = "minecraft:nuke/nuke_flash.png",
        ascent = 128,
        height = 256,
        chars = hashSetOf("\uE000")
    )
) {}