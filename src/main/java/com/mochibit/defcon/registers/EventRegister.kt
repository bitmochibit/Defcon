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

package com.mochibit.defcon.registers

import com.mochibit.defcon.Defcon
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException

class EventRegister() {
    private var packageName: String = Defcon.instance.javaClass.getPackage().name
    var plugin: JavaPlugin = JavaPlugin.getPlugin(Defcon::class.java)


    fun registerEvents() : EventRegister {
        plugin.getLogger().info("Registering listeners from $packageName.listeners")
        for (listenerClass in Reflections("$packageName.listeners").getSubTypesOf(Listener::class.java)) {
            try {
                plugin.server.pluginManager.registerEvents(
                    listenerClass.getDeclaredConstructor().newInstance(),
                    plugin
                )
            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }
        }

        return this;
    }
}
