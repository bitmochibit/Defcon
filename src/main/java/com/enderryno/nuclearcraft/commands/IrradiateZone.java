package com.enderryno.nuclearcraft.commands;

import com.enderryno.nuclearcraft.abstracts.GenericCommand;
import com.enderryno.nuclearcraft.annotations.CommandInfo;
import com.enderryno.nuclearcraft.services.Effector;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@CommandInfo(name="ncirradiatezone", permission = "nuclearcraft.admin", requiresPlayer = true)
public class IrradiateZone extends GenericCommand {
    @Override
    public void execute(Player player, String[] args) {
        int x, y, z, radius;
        // The first, second and third are the coordinates of the center;
        // The fourth is the radius;
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "You must specify the coordinates and the radius");
            return;
        }
        try {
            x = Integer.parseInt(args[0]);
            y = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
            radius = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "The parameters must be integers");
            return;
        }
        if (radius < 1) {
            player.sendMessage(ChatColor.RED + "The radius must be greater than 0");
            return;
        }
        Effector.generateRadiationField(new Location(player.getWorld(), x, y, z), radius);

    }
}
