package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.classes.CustomItem;
import com.enderryno.nuclearcraft.enums.BlockBehaviour;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import org.bukkit.Location;


public interface PluginBlock {
    PluginBlock setID(String id);
    String getID();

    PluginBlock setCustomModelId(int customModelId);
    int getCustomModelId();

    PluginBlock setMinecraftId(String minecraftId);
    String getMinecraftId();

    void placeBlock(PluginItem item, Location location);
    PluginBlock getBlock(Location location);
    void removeBlock(Location location);

    /*Behaviour type*/
    PluginBlock setBehaviour(BlockBehaviour behaviour);
    BlockBehaviour getBehaviour();


}
