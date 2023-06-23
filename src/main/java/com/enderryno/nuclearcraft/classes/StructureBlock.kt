package com.enderryno.nuclearcraft.classes

import com.enderryno.nuclearcraft.interfaces.PluginBlock
import org.bukkit.Location
data class StructureBlock(
    var block: PluginBlock,
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,
    var isInterface: Boolean = false
)
