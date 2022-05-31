package com.enderryno.nuclearcraft;

import com.enderryno.nuclearcraft.CustomItems.ItemRegister.ItemRegister;
import org.bukkit.plugin.java.JavaPlugin;

public final class NuclearCraft extends JavaPlugin {

    @Override
    public void onEnable() {
        this.getLogger().info("[NuclearCraft] has been enabled!");

        ItemRegister itemRegister = new ItemRegister(this);
        itemRegister.registerItems();


    }

    @Override
    public void onDisable() {
        this.getLogger().info("[NuclearCraft] has been disabled!");
    }
}
