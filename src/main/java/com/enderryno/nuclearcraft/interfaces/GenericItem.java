package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import org.bukkit.inventory.ItemStack;

public interface GenericItem {

    // Base properties

    GenericItem setName(String name);
    GenericItem setDescription(String description);
    GenericItem setID(int id);
    GenericItem setMinecraftId(String minecraftId);

    String getName();
    String getDisplayName();
    String getDescription();
    int getID();
    String getMinecraftId();


    ItemStack getItemStack();

    // Characteristics

    GenericItem setModelId(int modelId);
    GenericItem setCustomBlockId(int customBlockId);

    GenericItem setUsable(boolean usable);
    GenericItem setEquipable(boolean equipable);
    GenericItem setDroppable(boolean droppable);
    GenericItem setStackSize(int stackSize);
    GenericItem setTransportable(boolean transportable);


    int getModelId();
    int getCustomBlockId();

    boolean isUsable();
    boolean isEquipable();
    boolean isDroppable();
    int getStackSize();
    boolean isTransportable();

    /*Behaviour type*/
    GenericItem setBehaviour(ItemBehaviour behaviour);
    ItemBehaviour getBehaviour();


}
