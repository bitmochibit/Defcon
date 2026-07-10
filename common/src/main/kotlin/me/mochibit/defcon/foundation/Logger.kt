package me.mochibit.defcon.foundation
import com.mojang.logging.LogUtils
object Logger {
    private val logger = LogUtils.getLogger()
    private const val PREFIX = "[Defcon] "

    fun info(message: String) {
        logger.info(PREFIX + message)
    }

    fun warn(message: String) {
        logger.warn(PREFIX + message)
    }

    fun err(message: String) {
        logger.error(PREFIX + message)
    }

    fun debug(message: String) {
        logger.info(PREFIX + "~ DEBUG ~" + message)
    }
}

fun String.info() = Logger.info(this)

fun String.warn() = Logger.warn(this)

fun String.err() = Logger.err(this)

fun String.debug() = Logger.debug(this)