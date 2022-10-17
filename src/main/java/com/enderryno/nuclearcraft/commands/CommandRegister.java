package com.enderryno.nuclearcraft.commands;

import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;

public class CommandRegister {
    private final String packageName = getClass().getPackage().getName();

    private JavaPlugin pluginInstance = null;

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    public CommandRegister(JavaPlugin pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public void registerCommands() {
        for(Class<? extends GenericCommand> commandClass : new Reflections(this.packageName + ".definitions").getSubTypesOf(GenericCommand.class)) {
            try {
                GenericCommand genericCommand = commandClass.getDeclaredConstructor().newInstance();
                this.pluginInstance.getCommand(genericCommand.getCommandInfo().name()).setExecutor(genericCommand);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
