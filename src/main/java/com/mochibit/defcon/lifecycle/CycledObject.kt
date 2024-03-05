package com.mochibit.defcon.lifecycle

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.threading.jobs.Schedulable

abstract class CycledObject : Lifecycled, Schedulable {
    private var destroyed = false;
    fun instantiate(async: Boolean) {
        this.start();
        if (async) {
            Defcon.instance.asyncRunnable.addWorkload(this);
        } else {
            Defcon.instance.scheduledRunnable.addWorkload(this);
        }
    }
    fun destroy() {
        this.stop();
        destroyed = true;
    }

    override fun compute() {
        this.update(1.0 / Defcon.instance.server.tps[0])
    }

    override fun shouldBeRescheduled(): Boolean {
        return !destroyed;
    }


}