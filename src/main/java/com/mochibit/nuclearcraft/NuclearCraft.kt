package com.mochibit.nuclearcraft

import com.mochibit.nuclearcraft.database.Database
import com.mochibit.nuclearcraft.services.*
import com.mochibit.nuclearcraft.threading.tasks.ScheduledRunnable
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

class NuclearCraft : JavaPlugin() {
    val scheduledRunnable: ScheduledRunnable = ScheduledRunnable()
    override fun onEnable() {
        instance = this

        getLogger().info("[NuclearCraft] has been enabled!")
        /* Register all plugin's events */
        EventRegister()
                .registerItemEvents()
                .registerBlockEvents()

        /* Register custom items */
        if (!ItemRegister().registerItems()) {
            getLogger().warning("[NuclearCraft] Some items were not registered!")
        }

        /* Register custom blocks */
        BlockRegister().registerBlocks()


        /* Register commands */
        CommandRegister().registerCommands()

        /* Register structures */
        StructureRegister().registerStructures()

        // TODO: Thread pool for the scheduledRunnable, so there's a static class and there are a dynamic number of scheduledRunnable instances.
        // For example, if we drop 5 nukes, we want to have 5 scheduledRunnable instances running at the same time, not just one, so we can process the nukes in parallel
        // (for now they are processed sequentially)
        Bukkit.getScheduler().runTaskTimer(this, this.scheduledRunnable, 0L, 1L);
    }

    override fun onDisable() {
        getLogger().info("[NuclearCraft] has been disabled!")
        // Disconnect from all databases
        Database.disconnectAll()
    }

    companion object {
        lateinit var instance: NuclearCraft;

        var namespace = "nuclearcraft"
        object Logger {
            fun info(message: String) {
                instance.getLogger().info(message)
            }
            fun warning(message: String) {
                instance.getLogger().warning(message)
            }
            fun severe(message: String) {
                instance.getLogger().severe(message)
            }
        }
    }
}
