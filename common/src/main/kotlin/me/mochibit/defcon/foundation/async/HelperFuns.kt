package me.mochibit.defcon.foundation.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.defcon.foundation.services.PlatformService
import me.mochibit.defcon.foundation.services.platformService
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

val currentMainDispatcher: CoroutineContext
    get() =
        if (platformService.currentThreadSide ==
            PlatformService.Environment.SERVER
        ) {
            ModDispatchers.Server()
        } else {
            ModDispatchers.Client()
        }

private fun currentScope(): CoroutineScope =
    if (platformService isEnvironment
        PlatformService.Environment.CLIENT
    ) {
        ClientCoroutineScope
    } else {
        ServerCoroutineScope
    }

suspend fun onClientThread(block: suspend CoroutineScope.() -> Unit) = withContext(ModDispatchers.Client(), block)

suspend fun onServerThread(block: suspend CoroutineScope.() -> Unit) = withContext(ModDispatchers.Server(), block)

fun launchOnClient(block: suspend CoroutineScope.() -> Unit) = ClientCoroutineScope.launch(ModDispatchers.Client(), block = block)

fun launchOnServer(block: suspend CoroutineScope.() -> Unit) = ServerCoroutineScope.launch(ModDispatchers.Server(), block = block)

fun modLaunch(
    context: CoroutineContext = Dispatchers.Default,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context, block = block)

fun delayedLaunch(
    context: CoroutineContext = Dispatchers.Default,
    delay: Duration,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context) {
    delay(delay)
    block()
}

infix fun Duration.thenLaunch(block: suspend CoroutineScope.() -> Unit) = delayedLaunch(delay = this, block = block)

fun repeatingLaunch(
    context: CoroutineContext = Dispatchers.Default,
    initialDelay: Duration = Duration.ZERO,
    delay: Duration,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context) {
    if (initialDelay.inWholeMilliseconds > 0) {
        delay(initialDelay)
    }
    while (isActive) {
        block()
        if (delay.inWholeMilliseconds > 0) {
            delay(delay)
        }
    }
}

infix fun Duration.every(block: suspend CoroutineScope.() -> Unit) = repeatingLaunch(delay = this, block = block)

suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T =
    withContext(
        currentMainDispatcher,
        block,
    )