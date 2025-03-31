/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.notification

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.threading.scheduling.interval
import me.mochibit.defcon.utils.getComponentWithGradient
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.io.Closeable
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createDirectories

/**
 * Manages plugin notifications for the DEFCON plugin.
 * Handles broadcasting, displaying, and persisting notifications.
 */
object NotificationManager {
    // Configuration
    private val CONFIG_FILENAME = "notifications.json"
    private const val DEFAULT_BROADCAST_INTERVAL_SECONDS = 60

    // File management
    private val notificationFile: File by lazy {
        val path = Paths.get(Defcon.instance.dataFolder.path, CONFIG_FILENAME)
        path.parent.createDirectories() // Ensure directory exists
        path.toFile().apply {
            if (!exists()) createNewFile()
        }
    }

    // Tools
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    private val miniMessage = MiniMessage.miniMessage()

    // State
    private var broadcastTask: Closeable? = null
    private val notifications = CopyOnWriteArrayList<Notification>() // Thread-safe collection
    private var notificationIndex: Int = 0
    private var broadcastIntervalSeconds: Int = DEFAULT_BROADCAST_INTERVAL_SECONDS

    init {
        try {
            loadNotifications()
            Defcon.instance.logger.info("NotificationManager initialized with ${notifications.size} notifications")
        } catch (e: Exception) {
            Defcon.instance.logger.severe("Failed to initialize NotificationManager: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Starts the notification broadcast task.
     * @param intervalSeconds The interval between broadcasts in seconds.
     */
    fun startBroadcastTask(intervalSeconds: Int = DEFAULT_BROADCAST_INTERVAL_SECONDS) {
        stopBroadcastTask()
        this.broadcastIntervalSeconds = intervalSeconds

        broadcastTask = interval(20L, 20L * broadcastIntervalSeconds.toLong()) {
            if (notifications.isEmpty()) return@interval

            val notification = notifications[notificationIndex]
            broadcastNotification(notification)

            notificationIndex = (notificationIndex + 1) % notifications.size
        }

        Defcon.instance.logger.info("Notification broadcast task started with interval of $intervalSeconds seconds")
    }

    /**
     * Stops the notification broadcast task.
     */
    fun stopBroadcastTask() {
        broadcastTask?.close()
        broadcastTask = null
        Defcon.instance.logger.info("Notification broadcast task stopped")
    }

    /**
     * Broadcasts a notification to all online players.
     * @param notification The notification to broadcast.
     */
    private fun broadcastNotification(notification: Notification) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return

        onlinePlayers.forEach { player ->
            showNotification(player, notification)
        }
    }

    /**
     * Shows a notification to a specific audience.
     * @param audience The audience to show the notification to.
     * @param notification The notification to show.
     */
    fun showNotification(audience: Audience, notification: Notification) {
        try {
            val message = miniMessage.deserialize(notification.message)
            val prefix = getPrefix(notification)
            val formattedMessage = prefix.appendSpace().append(message)

            audience.sendMessage(formattedMessage)
            playNotificationSound(audience, notification.type)
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Failed to show notification: ${e.message}")
        }
    }

    /**
     * Plays a sound corresponding to the notification type.
     * @param audience The audience to play the sound to.
     * @param type The type of notification.
     */
    private fun playNotificationSound(audience: Audience, type: NotificationType) {
        val soundBuilder = Sound.sound()

        when (type) {
            NotificationType.INFO -> {
                soundBuilder.type(Key.key("minecraft", "block.note_block.pling"))
                    .volume(0.5f)
                    .pitch(1.0f)
            }
            NotificationType.WARNING -> {
                soundBuilder.type(Key.key("minecraft", "entity.experience_orb.pickup"))
                    .volume(0.5f)
                    .pitch(0.8f)
            }
            NotificationType.ERROR -> {
                soundBuilder.type(Key.key("minecraft", "entity.wither.spawn"))
                    .volume(0.3f)
                    .pitch(1.5f)
            }
            NotificationType.SUCCESS -> {
                soundBuilder.type(Key.key("minecraft", "entity.player.levelup"))
                    .volume(0.5f)
                    .pitch(1.2f)
            }
        }

        audience.playSound(soundBuilder.build(), Sound.Emitter.self())
    }

    /**
     * Gets the prefix component for a notification.
     * @param notification The notification to get the prefix for.
     * @return The prefix component.
     */
    private fun getPrefix(notification: Notification): Component {
        return when (notification.type) {
            NotificationType.INFO -> getComponentWithGradient(
                "DEFCON ☢",
                bold = true,
                colors = listOf("#74ebd5", "#ACB6E5")
            )
            NotificationType.WARNING -> getComponentWithGradient(
                "DEFCON ☢",
                bold = true,
                colors = listOf("#f12711", "#f5af19")
            )
            NotificationType.ERROR -> getComponentWithGradient(
                "DEFCON ☢",
                bold = true,
                colors = listOf("#FF416C", "#FF4B2B")
            )
            NotificationType.SUCCESS -> getComponentWithGradient(
                "DEFCON ☢",
                bold = true,
                colors = listOf("#11998e", "#38ef7d")
            )
        }
    }

    /**
     * Adds a notification to the manager.
     * @param message The message component.
     * @param type The type of notification.
     * @param saveToFile Whether to save the notification to file.
     * @return The added notification.
     */
    fun addNotification(
        message: Component,
        type: NotificationType = NotificationType.INFO,
        saveToFile: Boolean = false
    ): Notification {
        val notification = Notification(
            message = miniMessage.serialize(message),
            type = type,
            saveToFile = saveToFile,
            timestamp = System.currentTimeMillis()
        )

        notifications.add(notification)

        if (saveToFile) {
            saveNotifications()
        }

        return notification
    }

    /**
     * Adds a notification to the manager using a string message.
     * @param message The message string.
     * @param type The type of notification.
     * @param saveToFile Whether to save the notification to file.
     * @return The added notification.
     */
    fun addNotification(
        message: String,
        type: NotificationType = NotificationType.INFO,
        saveToFile: Boolean = false
    ): Notification {
        return addNotification(miniMessage.deserialize(message), type, saveToFile)
    }

    /**
     * Removes a notification from the manager.
     * @param notification The notification to remove.
     */
    fun removeNotification(notification: Notification) {
        notifications.remove(notification)
        saveNotifications()
    }

    /**
     * Clears all notifications.
     * @param saveChanges Whether to save changes to file.
     */
    fun clearNotifications(saveChanges: Boolean = true) {
        notifications.clear()
        if (saveChanges) {
            saveNotifications()
        }
    }

    /**
     * Saves notifications to file.
     */
    fun saveNotifications() {
        try {
            FileWriter(notificationFile).use { writer ->
                val notificationsToSave = notifications.filter { it.saveToFile }
                gson.toJson(notificationsToSave, writer)
            }
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Failed to save notifications: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Loads notifications from file.
     */
    private fun loadNotifications() {
        try {
            if (notificationFile.length() <= 0) return

            FileReader(notificationFile).use { reader ->
                val typeToken = object : TypeToken<List<Notification>>() {}.type
                val loadedNotifications: List<Notification> = gson.fromJson(reader, typeToken)

                // Only add notifications that are meant to be saved
                notifications.addAll(loadedNotifications.filter { it.saveToFile })
            }
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Failed to load notifications: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Gets all current notifications.
     * @return A list of all notifications.
     */
    fun getNotifications(): List<Notification> {
        return notifications.toList()
    }

    /**
     * Gets the current broadcast interval in seconds.
     * @return The broadcast interval.
     */
    fun getBroadcastInterval(): Int {
        return broadcastIntervalSeconds
    }

    /**
     * Sets the broadcast interval in seconds.
     * @param seconds The new broadcast interval.
     */
    fun setBroadcastInterval(seconds: Int) {
        if (seconds < 1) {
            throw IllegalArgumentException("Broadcast interval must be at least 1 second")
        }

        this.broadcastIntervalSeconds = seconds

        // Restart broadcast task if it's running
        if (broadcastTask != null) {
            startBroadcastTask(seconds)
        }
    }
}

