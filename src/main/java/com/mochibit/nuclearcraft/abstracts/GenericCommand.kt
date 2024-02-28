package com.mochibit.nuclearcraft.abstracts

import com.mochibit.nuclearcraft.annotations.CommandInfo
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

abstract class GenericCommand : CommandExecutor {
    val commandInfo: CommandInfo = javaClass.getDeclaredAnnotation(CommandInfo::class.java)

    init {
        Objects.requireNonNull(commandInfo, "Commands must have a CommandInfo annotation")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (commandInfo.permission.isNotEmpty()) {
            if (!sender.hasPermission(commandInfo.permission)) {
                sender.sendMessage(ChatColor.RED.toString() + "You can't execute this command.")
                return true
            }
        }
        if (commandInfo.requiresPlayer) {
            if (sender !is Player) {
                sender.sendMessage(ChatColor.RED.toString() + "This command can be only executed from a player.")
                return true
            }
            execute(sender, args)
            return true
        }
        execute(sender, args)
        return true
    }

    open fun execute(player: Player, args: Array<String>) {}
    private fun execute(sender: CommandSender?, args: Array<String>?) {}
}
