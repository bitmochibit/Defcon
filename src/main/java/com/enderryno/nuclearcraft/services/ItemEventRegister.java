package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.NuclearCraft;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;

public class ItemEventRegister {

    String packageName = NuclearCraft.instance.getClass().getPackage().getName();

    JavaPlugin plugin = null;

    public ItemEventRegister(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    public void register() {
        plugin.getLogger().info("Registering events from " + this.packageName + ".events");
        for(Class<? extends Listener> listenerClass : new Reflections(this.packageName  + ".events").getSubTypesOf(Listener.class)) {
            try {
                plugin.getServer().getPluginManager().registerEvents(listenerClass.getDeclaredConstructor().newInstance(), plugin);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }


}
