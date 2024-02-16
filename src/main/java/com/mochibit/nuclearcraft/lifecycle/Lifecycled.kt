package com.mochibit.nuclearcraft.lifecycle

interface Lifecycled {
    fun start()
    fun update(delta: Double)

    fun stop()
}