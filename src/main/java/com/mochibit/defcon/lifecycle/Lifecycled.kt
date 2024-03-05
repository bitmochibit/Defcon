package com.mochibit.defcon.lifecycle

interface Lifecycled {
    fun start()
    fun update(delta: Double)

    fun stop()
}