package com.enderryno.nuclearcraft.services

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.abstracts.GenericCommand
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException

class CommandRegister() {
    private val packageName: String = NuclearCraft.Companion.instance!!.javaClass.getPackage().name
    private var plugin: JavaPlugin = JavaPlugin.getPlugin(NuclearCraft::class.java)

    fun registerCommands() {
        plugin.getLogger().info("Registering commands from " + packageName + ".commands")
        for (commandClass in Reflections(packageName + ".commands").getSubTypesOf(GenericCommand::class.java)) {
            try {
                val genericCommand = commandClass.getDeclaredConstructor().newInstance()
                plugin.getCommand(genericCommand.commandInfo.name)!!.setExecutor(genericCommand)
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
