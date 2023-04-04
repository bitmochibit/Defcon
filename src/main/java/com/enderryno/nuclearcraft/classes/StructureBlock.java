package com.enderryno.nuclearcraft.classes;

import com.enderryno.nuclearcraft.interfaces.PluginBlock;
import org.bukkit.Location;
import org.bukkit.Material;

public class StructureBlock {
    private PluginBlock block;
    int x, y, z;


    public StructureBlock(PluginBlock block){
        this.block = block;
    }

    public StructureBlock setBlock(PluginBlock block) {
        this.block = block;
        return this;
    }

    public StructureBlock setPosition(Location location) {
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        return this;
    }

    public StructureBlock setPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public PluginBlock getBlock() {
        return this.block;
    }
}
