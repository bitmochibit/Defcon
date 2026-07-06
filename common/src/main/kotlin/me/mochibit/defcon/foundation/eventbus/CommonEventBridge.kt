package me.mochibit.defcon.foundation.eventbus

import kotlin.reflect.KClass

/**
 * This class keeps a reference for common events; **client** do have _server_ classes
 *
 * The opposite doesn't apply, so in that cases, for safety, use [ClientEventBridge]
 */
abstract class CommonEventBridge<PE : Any> : PlatformEventBridge<PE>() {
    override fun onServerRegistered(klass: KClass<out ServerProxyEvent>) {
        check(klass in serverTracking) { "Unknown ServerProxyEvent: ${klass.simpleName}" }
        serverTracking[klass] = true
    }

    override fun onClientRegistered(klass: KClass<out ClientProxyEvent>) {
        check(klass in clientTracking) { "Unknown ServerProxyEvent: ${klass.simpleName}" }
        clientTracking[klass] = true
    }

    override fun setup() {
        setupProxyEvents()
    }
}