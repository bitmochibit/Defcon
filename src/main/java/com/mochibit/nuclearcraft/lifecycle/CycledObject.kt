package com.mochibit.nuclearcraft.lifecycle

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.threading.jobs.Schedulable

abstract class CycledObject : Lifecycled, Schedulable {
    private var destroyed = false;
    fun instantiate(async: Boolean) {
        this.start();
        if (async) {
            NuclearCraft.instance.asyncRunnable.addWorkload(this);
        } else {
            NuclearCraft.instance.scheduledRunnable.addWorkload(this);
        }
    }
    fun destroy() {
        this.stop();
        destroyed = true;
    }

    override fun compute() {
        this.update(1.0 / NuclearCraft.instance.server.tps[0])
    }

    override fun shouldBeRescheduled(): Boolean {
        return !destroyed;
    }


}