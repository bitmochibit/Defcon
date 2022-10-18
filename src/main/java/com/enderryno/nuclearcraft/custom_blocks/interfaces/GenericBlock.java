package com.enderryno.nuclearcraft.custom_blocks.interfaces;

import com.enderryno.nuclearcraft.custom_items.interfaces.GenericItem;
import com.enderryno.nuclearcraft.custom_items.register.enums.ItemBehaviour;

public interface GenericBlock {
    GenericItem setID(int id);
    int getID();

    GenericItem setMinecraftId(String minecraftId);
    String getMinecraftId();

    GenericItem setCustomBlockId(int customBlockId);
    int getCustomBlockId();

    void placeBlock(double x, double y, double z);
    void removeBlock(double x, double y, double z);

    /*Behaviour type*/
    GenericItem setBehaviour(ItemBehaviour behaviour);
    ItemBehaviour getBehaviour();


}
