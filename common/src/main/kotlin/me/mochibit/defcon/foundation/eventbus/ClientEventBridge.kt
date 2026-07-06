package me.mochibit.defcon.foundation.eventbus

import kotlin.reflect.KClass

abstract class ClientEventBridge<PE : Any> : PlatformEventBridge<PE>() {
    override fun onServerRegistered(klass: KClass<out ServerProxyEvent>) {
        error("This bridge is only for client events!")
    }

    override fun onClientRegistered(klass: KClass<out ClientProxyEvent>) {
        check(klass in clientTracking) { "Unknown client-only ProxyEvent: ${klass.simpleName}" }
        clientTracking[klass] = true
    }

    override fun <FE : PE> registerServerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    ) = error("Use CommonEventBridge for server and common events")

    override fun setup() {
        setupProxyEvents()
    }
}