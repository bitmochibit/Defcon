package com.enderryno.nuclearcraft;

import com.enderryno.nuclearcraft.database.Database;
import com.enderryno.nuclearcraft.services.*;
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

        /* Register structures */
        new StructureRegister(this).registerStructures();

    }
    @Override
    public void onDisable() {
        this.getLogger().info("[NuclearCraft] has been disabled!");
        // Disconnect from all databases
        Database.disconnectAll();
    }
}
