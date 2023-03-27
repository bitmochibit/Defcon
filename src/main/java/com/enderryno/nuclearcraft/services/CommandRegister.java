package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.abstracts.GenericCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;

public class CommandRegister {
    private final String packageName = NuclearCraft.instance.getClass().getPackage().getName();

    private JavaPlugin plugin = null;

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    public CommandRegister(JavaPlugin pluginInstance) {
        this.plugin = pluginInstance;
    }

    public void registerCommands() {
        plugin.getLogger().info("Registering commands from " + this.packageName + ".commands");
        for(Class<? extends GenericCommand> commandClass : new Reflections(this.packageName + ".commands").getSubTypesOf(GenericCommand.class)) {
            try {
                GenericCommand genericCommand = commandClass.getDeclaredConstructor().newInstance();
                this.plugin.getCommand(genericCommand.getCommandInfo().name()).setExecutor(genericCommand);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
