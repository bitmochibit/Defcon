package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.NuclearCraft;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;

public class EventRegister {

    String packageName = NuclearCraft.instance.getClass().getPackage().getName();

    JavaPlugin plugin = null;

    public EventRegister(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    public EventRegister registerItemEvents() {
        plugin.getLogger().info("Registering events from " + this.packageName + ".events");
        for(Class<? extends Listener> listenerClass : new Reflections(this.packageName  + ".events.items").getSubTypesOf(Listener.class)) {
            try {
                plugin.getServer().getPluginManager().registerEvents(listenerClass.getDeclaredConstructor().newInstance(), plugin);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }
    public EventRegister registerBlockEvents() {
        plugin.getLogger().info("Registering events from " + this.packageName + ".events");
        for(Class<? extends Listener> listenerClass : new Reflections(this.packageName  + ".events.blocks").getSubTypesOf(Listener.class)) {
            try {
                plugin.getServer().getPluginManager().registerEvents(listenerClass.getDeclaredConstructor().newInstance(), plugin);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }


}
