package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.enums.BlockBehaviour;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;

public interface PluginBlock {
    PluginBlock setID(int id);
    int getID();

    PluginBlock setMinecraftId(String minecraftId);
    String getMinecraftId();

    PluginBlock setCustomBlockId(int customBlockId);
    int getCustomBlockId();

    void placeBlock(double x, double y, double z);
    void removeBlock(double x, double y, double z);

    /*Behaviour type*/
    PluginBlock setBehaviour(BlockBehaviour behaviour);
    BlockBehaviour getBehaviour();


}
