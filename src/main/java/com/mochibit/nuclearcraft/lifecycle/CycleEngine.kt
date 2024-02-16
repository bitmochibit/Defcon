package com.mochibit.nuclearcraft.lifecycle

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.threading.jobs.Schedulable
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob

// This will be likely renamed, but it will make work Lifecycle-able classes
class CycleEngine : Runnable {

    private val lifecycledObjects: HashSet<Lifecycled> = HashSet();


    fun add(lifecycledObject: Lifecycled) {
        lifecycledObject.start();
        lifecycledObjects.add(lifecycledObject);
    }

    private fun update() {
        // We get the delta time from the current TPS
        val delta = 1.0 / 20;
        lifecycledObjects.forEach {
            it.update(delta);
        }
    }
    fun remove(lifecycledObject: Lifecycled) {
        lifecycledObject.stop();
        lifecycledObjects.remove(lifecycledObject);
    }

    override fun run() {
        update();
    }



}