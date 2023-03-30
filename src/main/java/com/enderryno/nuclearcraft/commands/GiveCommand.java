package com.enderryno.nuclearcraft.commands;

import com.enderryno.nuclearcraft.abstracts.GenericCommand;
import com.enderryno.nuclearcraft.annotations.CommandInfo;
import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.services.ItemRegister;
import com.enderryno.nuclearcraft.exceptions.ItemNotRegisteredException;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@CommandInfo(name="ncgive", permission = "nuclearcraft.admin", requiresPlayer = true)
public class GiveCommand extends GenericCommand {
    @Override
    public void execute(Player player, String[] args) {
        String itemId;
        PluginItem item;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "The second parameter must be a NuclearCraft ID");
            return;
        }
        try {
            itemId = args[0];
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "The second parameter must be a NuclearCraft ID");
            return;
        }

        try {
            item = ItemRegister.getRegisteredItems().get(itemId);
        } catch (ItemNotRegisteredException e) {
            e.printStackTrace();
            return;
        }
        if (item == null) {
            player.sendMessage(ChatColor.RED + "This item is invalid");
            return;
        }
        try {
            player.getInventory().addItem(item.getItemStack());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Something went wrong, look for the console");
            e.printStackTrace();
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Gave the player a " + item.getDisplayName());

    }
}
