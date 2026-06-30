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
package me.mochibit.defcon.events

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.DefconPlugin
import me.mochibit.defcon.utils.Logger.info
import me.mochibit.defcon.utils.compareVersions
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.reflections.Reflections
import java.lang.reflect.Modifier

object EventRegister {
    private val logger = Defcon.logger
    private val currentVersion: String = Bukkit.getBukkitVersion().split("-")[0]
    private val registeredListeners = mutableSetOf<Class<*>>()


    fun registerPacketListeners(): EventRegister {
        info("Registering annotated packet listeners")

        val reflections = Reflections(Defcon::class.java.packageName)
        val annotatedClasses = reflections.getTypesAnnotatedWith(AutoRegisterHandler::class.java)
            .filter { PacketListener::class.java.isAssignableFrom(it) }

        logger.info("Found ${annotatedClasses.size} annotated packet listener classes")

        var registeredCount = 0
        for (clazz in annotatedClasses) {
            if (shouldRegisterAnnotatedClass(clazz) && registerPacketListener(clazz)) {
                registeredCount++
            }
        }

        logger.info("Registered $registeredCount packet listeners")
        return this
    }


    fun registerBukkitListeners(): EventRegister {
        info("Registering annotated Bukkit listeners")

        val reflections = Reflections(Defcon::class.java.packageName)
        val annotatedClasses = reflections.getTypesAnnotatedWith(AutoRegisterHandler::class.java)
            .filter { Listener::class.java.isAssignableFrom(it) }

        logger.info("Found ${annotatedClasses.size} annotated Bukkit listener classes")

        var registeredCount = 0
        for (clazz in annotatedClasses) {
            if (shouldRegisterAnnotatedClass(clazz) && registerBukkitListener(clazz)) {
                registeredCount++
            }
        }

        logger.info("Registered $registeredCount Bukkit listeners")
        return this
    }


    private fun registerPacketListener(clazz: Class<*>): Boolean {
        return try {
            logger.info("Registering packet listener: ${clazz.simpleName}")
            val listenerObj = clazz.getDeclaredConstructor().newInstance() as PacketListener

            // Get priority from annotation
            val annotation = clazz.getAnnotation(AutoRegisterHandler::class.java)
            val priority = annotation?.packetPriority ?: PacketListenerPriority.NORMAL

            val packetManager = PacketEvents.getAPI().eventManager
            packetManager.registerListener(listenerObj, priority)
            registeredListeners.add(clazz)
            true
        } catch (e: Exception) {
            logger.warning("Failed to register packet listener ${clazz.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }


    private fun registerBukkitListener(clazz: Class<*>): Boolean {
        return try {
            logger.info("Registering Bukkit listener: ${clazz.simpleName}")
            val listenerObj = clazz.getDeclaredConstructor().newInstance() as Listener

            val bukkitManager = Defcon.server.pluginManager
            bukkitManager.registerSuspendingEvents(listenerObj, Defcon)

            true
        } catch (e: Exception) {
            logger.warning("Failed to register Bukkit listener ${clazz.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun shouldRegisterAnnotatedClass(clazz: Class<*>): Boolean {
        if (registeredListeners.contains(clazz)) {
            logger.fine("Skipping already registered listener: ${clazz.simpleName}")
            return false
        }

        if (Modifier.isAbstract(clazz.modifiers) || clazz.isInterface) {
            logger.fine("Skipping abstract class or interface: ${clazz.simpleName}")
            return false
        }

        if (!clazz.name.startsWith(Defcon.javaClass.getPackage().name)) {
            logger.fine("Skipping external class: ${clazz.name}")
            return false
        }

        val autoRegister = clazz.getAnnotation(AutoRegisterHandler::class.java)
        if (autoRegister != null && !autoRegister.enabled) {
            logger.info("Skipping disabled listener: ${clazz.simpleName}")
            return false
        }

        // Check version compatibility
        if (!isCompatibleWithCurrentVersion(autoRegister)) {
            logger.info("Skipping listener ${clazz.simpleName} as it's not compatible with version $currentVersion (requires from ${autoRegister.fromVersion} to ${autoRegister.toVersion})")
            return false
        }

        return true
    }


    private fun isCompatibleWithCurrentVersion(registerAnnotation: AutoRegisterHandler): Boolean {
        val fromVersion = registerAnnotation.fromVersion
        val toVersion = registerAnnotation.toVersion

        val isCompatible = isVersionInRange(currentVersion, fromVersion, toVersion)

        return isCompatible
    }

    private fun isVersionInRange(version: String, fromVersion: String, toVersion: String): Boolean {
        if (fromVersion == "any" && toVersion == "latest") {
            return true
        }

        if (fromVersion.isNotEmpty() && compareVersions(version, fromVersion) < 0) {
            return false
        }

        if (toVersion != "latest" && toVersion.isNotEmpty() && compareVersions(version, toVersion) > 0) {
            return false
        }

        return true
    }
}