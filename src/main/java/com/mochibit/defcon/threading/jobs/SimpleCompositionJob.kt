package com.mochibit.defcon.threading.jobs

import java.util.function.Consumer

class SimpleCompositionJob<T> (val element: T, val action: Consumer<T>) : Schedulable {
    override fun compute() {
        action.accept(element)
    }

    override fun shouldBeRescheduled(): Boolean {
        return false;
    }
}