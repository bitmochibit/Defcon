package com.enderryno.nuclearcraft;

import com.enderryno.nuclearcraft.services.CommandRegister;
import com.enderryno.nuclearcraft.services.BlockRegister;
import com.enderryno.nuclearcraft.services.EventRegister;
import com.enderryno.nuclearcraft.services.ItemRegister;
import org.bukkit.plugin.java.JavaPlugin;
public final class NuclearCraft extends JavaPlugin {
    public static NuclearCraft instance = null;

    @Override
    public void onEnable() {
        if (NuclearCraft.instance == null)
            NuclearCraft.instance = this;

        this.getLogger().info("[NuclearCraft] has been enabled!");
        /* Register all plugin's events */
        new EventRegister(this)
                .registerItemEvents()
                .registerBlockEvents();

        /* Register custom items */
        if (!new ItemRegister(this).registerItems()) {
            this.getLogger().warning("[NuclearCraft] Some items were not registered!");
        }

        /* Register custom blocks */
        new BlockRegister(this).registerBlocks();


        /* Register commands */
        new CommandRegister(this).registerCommands();

    }
    @Override
    public void onDisable() {
        this.getLogger().info("[NuclearCraft] has been disabled!");
    }
}
