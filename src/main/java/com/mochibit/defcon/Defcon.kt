package com.mochibit.defcon

import com.mochibit.defcon.biomes.CustomBiomeHandler
import com.mochibit.defcon.services.*
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class Defcon : JavaPlugin() {

    // This whole stuff will be moved, this is just for testing
    val scheduledRunnable: ScheduledRunnable = ScheduledRunnable()
    val asyncRunnable: ScheduledRunnable = ScheduledRunnable()
    override fun onEnable() {
        instance = this

        getLogger().info("[Defcon] has been enabled!")
        // Register datapack
        DatapackRegister.get.registerPack()

        /* Register all plugin's events */
        EventRegister()
                .registerItemEvents()
                .registerBlockEvents()

        /* Register definitions items */
        if (!ItemRegister().registerItems()) {
            getLogger().warning("[Defcon] Some items were not registered!")
        }

        /* Register definitions blocks */
        BlockRegister().registerBlocks()


        /* Register commands */
        CommandRegister().registerCommands()

        /* Register structures */
        StructureRegister().registerStructures()

        // TODO: Thread pool for the scheduledRunnable, so there's a static class and there are a dynamic number of scheduledRunnable instances.
        // For example, if we drop 5 nukes, we want to have 5 scheduledRunnable instances running at the same time, not just one, so we can process the nukes in parallel
        // (for now they are processed sequentially)



        Bukkit.getScheduler().runTaskTimer(this, this.scheduledRunnable, 0L, 1L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this.asyncRunnable, 0L, 1L);
    }

    override fun onDisable() {
        getLogger().info("[Defcon] has been disabled!")
        // Disconnect from all databases
        //Database.disconnectAll()
    }

    companion object {
        lateinit var instance: Defcon;

        var namespace = "defcon"
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
