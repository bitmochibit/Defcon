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
import com.github.retrooper.packetevents.event.PacketEvent
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.listeners.packet.biome.ClientSideBiome
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException

object EventRegister {
    private var packageName: String = Defcon.instance.javaClass.getPackage().name
    var plugin: JavaPlugin = JavaPlugin.getPlugin(Defcon::class.java)


    fun registerPacketListeners(): EventRegister {
        val packetListeners = Reflections("$packageName.listeners.packet").getSubTypesOf(PacketListener::class.java)
        val packetManager = PacketEvents.getAPI().eventManager

        for (listener in packetListeners) {
            try {
                println("Registering packet listener: ${listener.name}")
                val listenerObj = listener.getDeclaredConstructor().newInstance()
                packetManager.registerListener(listenerObj, PacketListenerPriority.NORMAL)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        return this;
    }

    fun registerListeners() : EventRegister {
        plugin.logger.info("Registering listeners from $packageName.listeners")
        val bukkitListeners = Reflections("$packageName.listeners").getSubTypesOf(Listener::class.java)
        val bukkitManager = plugin.server.pluginManager

        for (listener in bukkitListeners) {
            try {
                println("Registering bukkit listener: ${listener.name}")
                val listenerObj = listener.getDeclaredConstructor().newInstance()
                bukkitManager.registerEvents(listenerObj, plugin)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        return this;
    }
}
