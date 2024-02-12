package com.mochibit.nuclearcraft.threading.jobs

interface Schedulable {
    fun compute()
    fun shouldBeRescheduled(): Boolean = false
}