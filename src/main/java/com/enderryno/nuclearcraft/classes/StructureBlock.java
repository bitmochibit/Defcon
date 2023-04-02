package com.enderryno.nuclearcraft.classes;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class StructureBlock<T> {
    private T block;
    int x, y, z;


    public StructureBlock(T block) throws Exception {
        if (!(block instanceof CustomBlock) && !(block instanceof Material)) {
            throw new Exception("The block must be a CustomBlock or a Block");
        }
        this.block = block;
    }

    public StructureBlock<T> setBlock(T block) {
        this.block = block;
        return this;
    }

    public StructureBlock<T> setPosition(Location location) {
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        return this;
    }

    public StructureBlock<T> setPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public T getBlock() {
        return this.block;
    }
}
