package com.mochibit.nuclearcraft.lifecycle

import com.mochibit.nuclearcraft.NuclearCraft

abstract class CycledObject : Lifecycled{
    fun instantiate() {
        NuclearCraft.instance.cycleEngine.add(this);
    }
    fun destroy() {
        NuclearCraft.instance.cycleEngine.remove(this);
    }

    fun instantiateAsync() {
        NuclearCraft.instance.asyncCycleEngine.add(this);
    }
    fun destroyAsync() {
        NuclearCraft.instance.asyncCycleEngine.remove(this);
    }
}