package com.mochibit.nuclearcraft.threading.jobs

import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

class ScheduledCompositionJob<T>(
    val valueSupplier: Supplier<T>,
    val breakupCondition: Predicate<T>,
    val valueConsumer: Consumer<T>
) : Schedulable {
    private var failed: Boolean = false;

    override fun compute() {
        val element: T = valueSupplier.get();
        if (breakupCondition.test(element)) {
            failed = true;
            return;
        }

        valueConsumer.accept(element);
    }

    override fun shouldBeRescheduled(): Boolean {
        return !failed;
    }

}