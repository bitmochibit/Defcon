package com.mochibit.defcon.effects

import com.mochibit.defcon.lifecycle.CycledObject

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