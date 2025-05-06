/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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
package me.mochibit.defcon.registers

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.registers.listener.UniversalVersionIndicator
import me.mochibit.defcon.registers.listener.VersionIndicator
import me.mochibit.defcon.utils.compareVersions
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.Modifier

object EventRegister {
    private val packageName: String = Defcon.instance.javaClass.getPackage().name
    private val plugin = Defcon.instance
    private val logger = plugin.logger

    // Get the current server version
    private val currentVersion: String = Bukkit.getBukkitVersion().split("-")[0]

    // Keep track of registered listener classes to prevent duplicates
    private val registeredListeners = mutableSetOf<Class<*>>()

    /**
     * Register all listeners and return this instance for chaining
     */
    fun registerAllListeners(): EventRegister {
        return registerPacketListeners().registerBukkitListeners()
    }

    /**
     * Register packet listeners that are compatible with the current server version
     */
    fun registerPacketListeners(): EventRegister {
        val packetListenersPackage = "$packageName.listeners.packet"
        logger.info("Scanning for packet listeners in $packetListenersPackage")

        try {
            val packetListeners = Reflections(packetListenersPackage).getSubTypesOf(PacketListener::class.java)
            val packetManager = PacketEvents.getAPI().eventManager

            for (listenerClass in packetListeners) {
                if (shouldRegisterClass(listenerClass)) {
                    try {
                        logger.info("Registering packet listener: ${listenerClass.simpleName}")
                        val listenerObj = listenerClass.getDeclaredConstructor().newInstance()
                        packetManager.registerListener(listenerObj, PacketListenerPriority.NORMAL)
                        registeredListeners.add(listenerClass)
                    } catch (e: Exception) {
                        logger.warning("Failed to register packet listener ${listenerClass.simpleName}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to scan package $packetListenersPackage: ${e.message}")
            e.printStackTrace()
        }

        return this
    }

    /**
     * Register Bukkit listeners that are compatible with the current server version
     */
    fun registerBukkitListeners(): EventRegister {
        val listenerPackages = listOf(
            "$packageName.listeners",
            "$packageName.listeners.items",
            "$packageName.listeners.blocks",
            "$packageName.listeners.entities"
        )

        logger.info("Scanning for Bukkit listeners in ${listenerPackages.size} packages")

        // Process each package for Bukkit listeners
        for (packagePath in listenerPackages) {
            registerListenersFromPackage(packagePath)
        }

        logger.info("Registered ${registeredListeners.count { it.interfaces.contains(Listener::class.java) }} Bukkit listeners total")
        return this
    }

    /**
     * Register all compatible listeners from a specific package
     */
    private fun registerListenersFromPackage(packagePath: String) {
        try {
            val reflections = Reflections(packagePath)
            val bukkitListeners = reflections.getSubTypesOf(Listener::class.java)
            val bukkitManager = plugin.server.pluginManager

            logger.info("Found ${bukkitListeners.size} potential listeners in $packagePath")

            for (listenerClass in bukkitListeners) {
                if (shouldRegisterClass(listenerClass)) {
                    try {
                        logger.info("Registering Bukkit listener: ${listenerClass.simpleName}")
                        val listenerObj = listenerClass.getDeclaredConstructor().newInstance()
                        bukkitManager.registerEvents(listenerObj, plugin)
                        registeredListeners.add(listenerClass)
                    } catch (e: Exception) {
                        logger.warning("Failed to register listener ${listenerClass.simpleName}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to scan package $packagePath: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Determine if a class should be registered based on version compatibility
     * and whether it has already been registered
     */
    private fun shouldRegisterClass(clazz: Class<*>): Boolean {
        // Skip if already registered
        if (registeredListeners.contains(clazz)) {
            logger.fine("Skipping already registered listener: ${clazz.simpleName}")
            return false
        }

        // Skip abstract classes or interfaces
        if (Modifier.isAbstract(clazz.modifiers) || clazz.isInterface) {
            logger.fine("Skipping abstract class or interface: ${clazz.simpleName}")
            return false
        }

        return isCompatibleWithCurrentVersion(clazz)
    }

    /**
     * Check if a class is compatible with the current server version based on annotations
     */
    private fun isCompatibleWithCurrentVersion(clazz: Class<*>): Boolean {
        // Always compatible if has the UniversalVersionIndicator annotation
        if (clazz.isAnnotationPresent(UniversalVersionIndicator::class.java)) {
            return true
        }

        // Check version range if has VersionIndicator annotation
        if (clazz.isAnnotationPresent(VersionIndicator::class.java)) {
            val annotation = clazz.getAnnotation(VersionIndicator::class.java)
            val fromVersion = annotation.fromVersion
            val toVersion = annotation.toVersion

            val isCompatible = isVersionInRange(currentVersion, fromVersion, toVersion)

            if (!isCompatible) {
                logger.info("Skipping listener ${clazz.simpleName} as it's not compatible with version $currentVersion (requires $fromVersion to $toVersion)")
            }

            return isCompatible
        }

        // By default, register classes without any version annotations
        return true
    }

    /**
     * Check if a version is within a specified range
     */
    private fun isVersionInRange(version: String, fromVersion: String, toVersion: String): Boolean {
        if (fromVersion.isNotEmpty() && compareVersions(version, fromVersion) < 0) {
            return false // Version is less than minimum version
        }

        if (toVersion != "latest" && toVersion.isNotEmpty() && compareVersions(version, toVersion) > 0) {
            return false // Version is greater than maximum version
        }

        return true
    }
}