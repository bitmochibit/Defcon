package com.mochibit.defcon.classes
import com.mochibit.defcon.interfaces.PluginBlock
data class StructureBlock(
    var block: PluginBlock,
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,
    var isInterface: Boolean = false
)
