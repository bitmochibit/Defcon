package me.mochibit.defcon

import me.mochibit.defcon.foundation.err

object DefconClientMod {
    private var initialized = false

    fun setup() {
        if (initialized) {
            return "Client side was already initialized".err()
        }
        initialized = true

//        PonderIndex.addPlugin(ModPonderPlugin())

    }
}