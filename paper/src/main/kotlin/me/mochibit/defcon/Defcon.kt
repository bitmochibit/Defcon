/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.config.PluginConfiguration
import me.mochibit.defcon.events.EventRegister
import me.mochibit.defcon.notification.NotificationManager
import me.mochibit.defcon.radiation.RadiationManager
import me.mochibit.defcon.registry.BlockRegistry
import me.mochibit.defcon.registry.CommandRegistry
import me.mochibit.defcon.registry.DatapackRegistry
import me.mochibit.defcon.registry.ItemRegistry
import me.mochibit.defcon.registry.ResourcePackRegistry
import me.mochibit.defcon.registry.StructureRegistry
import me.mochibit.defcon.server.ResourcePackServer
import me.mochibit.defcon.utils.Logger.info
import org.bukkit.Bukkit

class DefconPlugin : SuspendingJavaPlugin() {
    companion object {
        @JvmStatic
        lateinit var instance: DefconPlugin
            private set
    }

    init {
        instance = this
    }

    override fun onLoad() {
        info("Defcon is starting up ☢️")
        try {
            EventRegister.registerPacketListeners()
        } catch (e: Exception) {
            me.mochibit.defcon.utils.Logger
                .err("Failed to register packet listeners: ${e.message}")
            e.printStackTrace()
        }
    }

    override suspend fun onEnableAsync() {
        try {
            PluginConfiguration.loadAll()

            DatapackRegistry.register()
            ResourcePackRegistry.register()

            NotificationManager.startBroadcastTask()

            EventRegister.registerBukkitListeners()

            BlockRegistry.registerAll()
            ItemRegistry.registerAll()
            StructureRegistry.registerAll()
            CommandRegistry.registerCommands()

            RadiationManager.start()
            CustomBiomeHandler.initialize()
            ResourcePackServer.startServer()

            info("Defcon has been enabled successfully! ☢️")
        } catch (e: Exception) {
            me.mochibit.defcon.utils.Logger
                .err("Failed to enable Defcon: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override suspend fun onDisableAsync() {
        NotificationManager.saveNotifications()
        ResourcePackServer.stopServer()
        CustomBiomeHandler.shutdown()
        info("Plugin disabled!")
    }

    val minecraftVersion get() = Bukkit.getServer().bukkitVersion.split("-")[0]
}

val Defcon get() = DefconPlugin.instance

internal fun pluginNamespacedKey(key: String) = org.bukkit.NamespacedKey(Defcon, key)
