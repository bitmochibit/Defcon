package com.enderryno.nuclearcraft;

import com.enderryno.nuclearcraft.CustomItems.ItemListeners.Register.ItemEventRegister;
import com.enderryno.nuclearcraft.CustomItems.ItemRegister.ItemRegister;
import org.bukkit.plugin.java.JavaPlugin;

public final class NuclearCraft extends JavaPlugin {

    @Override
    public void onEnable() {
        this.getLogger().info("[NuclearCraft] has been enabled!");

        /* Registering all plugin's events */
        new ItemEventRegister(this).register();
        /* Registering custom items */
        new ItemRegister(this).registerItems();


    }

    @Override
    public void onDisable() {
        this.getLogger().info("[NuclearCraft] has been disabled!");
    }
}
