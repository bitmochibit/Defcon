package me.mochibit.defcon.foundation.eventbus


import kotlin.reflect.KClass

abstract class PlatformEventBridge<PE : Any> {
    companion object {
        protected val serverTracking: MutableMap<KClass<out ServerProxyEvent>, Boolean> =
            ServerProxyEvent::class
                .allSealedLeaves<ServerProxyEvent>()
                .associateWith { false }
                .toMutableMap()

        protected val clientTracking: MutableMap<KClass<out ClientProxyEvent>, Boolean> =
            ClientProxyEvent::class
                .allSealedLeaves<ClientProxyEvent>()
                .associateWith { false }
                .toMutableMap()

        fun validateAll(checkClientOnlyToo: Boolean) {
            val missingServer = serverTracking.filterValues { !it }.keys
            val missingClient =
                if (checkClientOnlyToo) {
                    clientTracking.filterValues { !it }.keys
                } else {
                    emptySet()
                }
            val missing = missingServer + missingClient
            if (missing.isNotEmpty()) {
                error(
                    "Unregistered proxy events!\n" +
                            missing.map { it.qualifiedName } +
                            "\nhttps://github.com/bitmochibit/createharmonics/issues",
                )
            }
        }
    }

    abstract fun <FE : PE> registerClientListener(
        klass: KClass<FE>,
        mapper: FE.() -> ClientProxyEvent,
    )

    abstract fun <FE : PE> registerServerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    )

    abstract fun onServerRegistered(klass: KClass<out ServerProxyEvent>)

    abstract fun onClientRegistered(klass: KClass<out ClientProxyEvent>)

    inner class ProxyBuilder<FE : PE>(
        val klass: KClass<FE>,
    ) {
        inline fun <reified E : ServerProxyEvent> registerServer(noinline mapper: FE.(LogicalSide) -> E) {
            onServerRegistered(E::class)
            registerServerListener(klass) { mapper(LogicalSide.SERVER) }
        }

        inline fun <reified E : ClientProxyEvent> registerClient(noinline mapper: FE.(LogicalSide) -> E) {
            onClientRegistered(E::class)
            registerClientListener(klass) { mapper(LogicalSide.CLIENT) }
        }

        inline fun <reified E> registerBoth(noinline mapper: FE.(LogicalSide) -> E) where E : ServerProxyEvent, E : ClientProxyEvent {
            onServerRegistered(E::class)
            onClientRegistered(E::class)
            registerServerListener(klass) { mapper(LogicalSide.SERVER) }
            registerClientListener(klass) { mapper(LogicalSide.CLIENT) }
        }
    }

    protected inline fun <reified FE : PE> on() = ProxyBuilder(FE::class)

    protected abstract fun setupProxyEvents()

    abstract fun setup()
}

fun <T : Any> KClass<*>.allSealedLeaves(): List<KClass<out T>> =
    if (sealedSubclasses.isEmpty()) {
        listOf(this as KClass<out T>)
    } else {
        sealedSubclasses.flatMap { it.allSealedLeaves() }
    }