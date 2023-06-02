package com.enderryno.nuclearcraft

import com.enderryno.nuclearcraft.database.Database
import com.enderryno.nuclearcraft.services.*
import org.bukkit.plugin.java.JavaPlugin

class NuclearCraft : JavaPlugin() {
    override fun onEnable() {
        if (instance == null) instance = this
        getLogger().info("[NuclearCraft] has been enabled!")
        /* Register all plugin's events */EventRegister()
                .registerItemEvents()
                .registerBlockEvents()

        /* Register custom items */if (!ItemRegister().registerItems()) {
            getLogger().warning("[NuclearCraft] Some items were not registered!")
        }

        /* Register custom blocks */BlockRegister().registerBlocks()


        /* Register commands */CommandRegister().registerCommands()

        /* Register structures */StructureRegister().registerStructures()
    }

    override fun onDisable() {
        getLogger().info("[NuclearCraft] has been disabled!")
        // Disconnect from all databases
        Database.disconnectAll()
    }

    companion object {
        var instance: NuclearCraft? = null
        object Logger {
            fun info(message: String) {
                instance!!.getLogger().info(message)
            }
            fun warning(message: String) {
                instance!!.getLogger().warning(message)
            }
            fun severe(message: String) {
                instance!!.getLogger().severe(message)
            }
        }
    }
}
