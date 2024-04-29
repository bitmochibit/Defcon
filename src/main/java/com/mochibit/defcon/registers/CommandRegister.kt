package com.mochibit.defcon.registers

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.commands.GenericCommand
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException

class CommandRegister() {
    private val packageName: String = Defcon.instance.javaClass.getPackage().name
    private var plugin: JavaPlugin = JavaPlugin.getPlugin(Defcon::class.java)

    fun registerCommands() {
        plugin.getLogger().info("Registering commands from $packageName.commands")
        for (commandClass in Reflections("$packageName.commands").getSubTypesOf(GenericCommand::class.java)) {
            try {
                val genericCommand = commandClass.getDeclaredConstructor().newInstance()
                // debug info
                plugin.getLogger().info("Registering command: ${genericCommand.commandInfo.name}")
                val pluginCommand = plugin.getCommand(genericCommand.commandInfo.name)
                if (pluginCommand == null) {
                    plugin.getLogger().warning("Command ${genericCommand.commandInfo.name} not found, cache issue?")
                    continue
                }

                pluginCommand.setExecutor(genericCommand);

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
    }
}
