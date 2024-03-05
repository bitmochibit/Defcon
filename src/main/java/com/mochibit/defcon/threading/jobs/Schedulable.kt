package com.mochibit.defcon.threading.jobs

interface Schedulable {
    fun compute()
    fun shouldBeRescheduled(): Boolean = false
}