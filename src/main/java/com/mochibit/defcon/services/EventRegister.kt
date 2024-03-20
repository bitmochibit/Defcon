package com.mochibit.defcon.services

import com.mochibit.defcon.Defcon
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException

class EventRegister() {
    private var packageName: String = Defcon.instance.javaClass.getPackage().name
    var plugin: JavaPlugin = JavaPlugin.getPlugin(Defcon::class.java)

    fun registerItemEvents(): EventRegister {
        plugin.getLogger().info("Registering events from $packageName.events")
        for (listenerClass in Reflections("$packageName.events.items").getSubTypesOf(Listener::class.java)) {
            try {
                plugin.server.pluginManager.registerEvents(listenerClass.getDeclaredConstructor().newInstance(),
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
        return this
    }

    fun registerBlockEvents(): EventRegister {
        plugin.getLogger().info("Registering events from $packageName.events")
        for (listenerClass in Reflections("$packageName.events.blocks").getSubTypesOf(Listener::class.java)) {
            try {
                plugin.server.pluginManager.registerEvents(listenerClass.getDeclaredConstructor().newInstance(), plugin)
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
        return this
    }
}
