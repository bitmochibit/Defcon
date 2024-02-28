package com.mochibit.nuclearcraft.effects

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.lifecycle.CycledObject
import com.mochibit.nuclearcraft.lifecycle.Lifecycled

abstract class AnimatedEffect : CycledObject()
{
    var tickAlive: Int = 0;


    abstract fun draw();

    abstract fun animate(delta: Double);

    override fun update(delta: Double) {
        tickAlive++;
        animate(delta);
        if (tickAlive % drawRate() == 0)
            draw();
    }

    open fun drawRate() : Int {
        return 1;
    }
}