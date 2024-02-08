package com.enderryno.nuclearcraft.commands

import com.enderryno.nuclearcraft.abstracts.GenericCommand
import com.enderryno.nuclearcraft.annotations.CommandInfo
import com.enderryno.nuclearcraft.exceptions.ItemNotRegisteredException
import com.enderryno.nuclearcraft.interfaces.PluginItem
import com.enderryno.nuclearcraft.services.ItemRegister
import org.bukkit.ChatColor
import org.bukkit.entity.Player

@CommandInfo(name = "ncgive", permission = "nuclearcraft.admin", requiresPlayer = true)
class GiveCommand : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        if (args.isEmpty()) {
            player.sendMessage(ChatColor.RED.toString() + "The second parameter must be a NuclearCraft ID")
            return
        }
        val itemId: String = try {
            args[0]
        } catch (ex: NumberFormatException) {
            player.sendMessage(ChatColor.RED.toString() + "The second parameter must be a NuclearCraft ID")
            return
        }
        val item: PluginItem? = try {
            ItemRegister.registeredItems[itemId]
        } catch (e: ItemNotRegisteredException) {
            e.printStackTrace()
            return
        }
        if (item == null) {
            player.sendMessage(ChatColor.RED.toString() + "This item is invalid")
            return
        }
        try {
            player.inventory.addItem(item.itemStack)
        } catch (e: Exception) {
            player.sendMessage(ChatColor.RED.toString() + "Something went wrong, look for the console")
            e.printStackTrace()
            return
        }
        player.sendMessage(ChatColor.GREEN.toString() + "Gave the player a " + item.displayName)
    }
}
