package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.enums.ItemBehaviour;

public interface PluginBlock {
    PluginItem setID(int id);
    int getID();

    PluginItem setMinecraftId(String minecraftId);
    String getMinecraftId();

    PluginItem setCustomBlockId(int customBlockId);
    int getCustomBlockId();

    void placeBlock(double x, double y, double z);
    void removeBlock(double x, double y, double z);

    /*Behaviour type*/
    PluginItem setBehaviour(ItemBehaviour behaviour);
    ItemBehaviour getBehaviour();


}
