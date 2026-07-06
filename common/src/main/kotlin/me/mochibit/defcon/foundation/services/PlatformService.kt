package me.mochibit.defcon.foundation.services

interface PlatformService {
    enum class Platform {
        NEOFORGE,
        FORGE,
    }

    enum class Environment {
        SERVER,
        CLIENT,
    }

    infix fun isEnvironment(env: Environment): Boolean = environment == env

    val currentPlatform: Platform

    /**
     * Physical dist
     */
    val environment: Environment

    /**
     * Logical side
     */
    val currentThreadSide: Environment

    fun isModLoaded(modId: String): Boolean

    fun setupEventBridge()

    fun setupClientEventBridge()
}

val platformService: PlatformService by lazy {
    loadService<PlatformService>()
}