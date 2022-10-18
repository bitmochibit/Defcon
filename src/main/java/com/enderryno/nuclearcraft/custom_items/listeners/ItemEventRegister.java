package com.enderryno.nuclearcraft.custom_items.listeners;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;

public class ItemEventRegister {

    String packageName = getClass().getPackage().getName();

    JavaPlugin plugin = null;

    public ItemEventRegister(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    public void register() {
        for(Class<? extends Listener> listenerClass : new Reflections(packageName).getSubTypesOf(Listener.class)) {
            try {
                plugin.getServer().getPluginManager().registerEvents(listenerClass.getDeclaredConstructor().newInstance(), plugin);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }


}
