package com.enderryno.nuclearcraft.abstracts;

import com.enderryno.nuclearcraft.annotations.CommandInfo;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class GenericCommand implements CommandExecutor {
    private final CommandInfo commandInfo;

    public GenericCommand() {
        commandInfo = getClass().getDeclaredAnnotation(CommandInfo.class);
        Objects.requireNonNull(commandInfo, "Commands must have a CommandInfo annotation");
    }

    public CommandInfo getCommandInfo() {
        return commandInfo;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!getCommandInfo().permission().isEmpty()) {
            if (!sender.hasPermission(commandInfo.permission())) {
                sender.sendMessage(ChatColor.RED + "You can't execute this command.");
                return true;
            }
        }

        if (commandInfo.requiresPlayer()) {
            if(!(sender instanceof  Player)) {
                sender.sendMessage(ChatColor.RED + "This command can be only executed from a player.");
                return true;
            }
            execute((Player)sender, args);
            return true;

        }

        execute(sender, args);
        return true;
    }

    public void execute(Player player, String[] args) {}
    public void execute(CommandSender sender, String[] args) {}

}
