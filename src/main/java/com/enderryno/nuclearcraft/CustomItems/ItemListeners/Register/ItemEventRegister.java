package com.enderryno.nuclearcraft.CustomItems.ItemListeners.Register;

import com.enderryno.nuclearcraft.CustomItems.ItemListeners.*;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemEventRegister {

    JavaPlugin plugin = null;

    public static Listener[] eventsToRegister = {
        new GasMaskListener(),
        new GasMaskFilterListener(),
        new GeigerCounterListener(),
        new RadiationInhibitorListener(),

        new GenericListener(),
    };


    public ItemEventRegister(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    public void register() {

        for (Listener event : eventsToRegister) {
            plugin.getServer().getPluginManager().registerEvents(event, plugin);
        }
    }


}
