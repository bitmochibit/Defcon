package com.mochibit.defcon

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.registers.*
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import com.mochibit.defcon.utils.MetaManager
import io.papermc.lib.PaperLib
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class Defcon : JavaPlugin() {

    // This whole stuff will be moved, this is just for testing
    val scheduledRunnable: ScheduledRunnable = ScheduledRunnable()
    val asyncRunnable: ScheduledRunnable = ScheduledRunnable()

    override fun onLoad() {
        instance = this

        // Register datapack
        DatapackRegister.get.registerPack()
    }

    override fun onEnable() {

        PaperLib.suggestPaper(this)

        getLogger().info("[Defcon] has been enabled!")

        info("Registering resource pack")
        ResourcePackRegister.get.registerPack()


        /* Register all plugin's events */
        EventRegister().registerEvents()

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


        //TODO: Refactor
        Bukkit.getScheduler().runTaskTimer(
            this, Runnable {
                // Loop through every player and check if they are in a radiation area
                for (player in Bukkit.getOnlinePlayers()) {
                    if (RadiationArea.checkIfInBounds(player.location)) {
                        player.damage(1.0)
                        continue;
                    }

                    val block = player.location.add(0.0, 1.0, 0.0).block
                    val radiationLevel = MetaManager.getBlockData<Double>(block.location, BlockDataKey.RadiationLevel)

                    if (radiationLevel != null && radiationLevel >= 1.0) {
                        player.damage(1.0)
                    }
                }
            },
            0, 20
        )

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
