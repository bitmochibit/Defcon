package com.enderryno.nuclearcraft.classes

import com.enderryno.nuclearcraft.interfaces.PluginBlock
import org.bukkit.Location

class StructureBlock(var block: PluginBlock?) {
    private var x = 0
    private var y = 0
    private var z = 0
    fun setBlock(block: PluginBlock?): StructureBlock {
        this.block = block
        return this
    }

    fun setPosition(location: Location): StructureBlock {
        x = location.blockX
        y = location.blockY
        z = location.blockZ
        return this
    }

    fun setPosition(x: Int, y: Int, z: Int): StructureBlock {
        this.x = x
        this.y = y
        this.z = z
        return this
    }
}
