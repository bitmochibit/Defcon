package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import org.bukkit.inventory.ItemStack;

public interface PluginItem {

    // Base properties

    PluginItem setName(String name);

    PluginItem setDescription(String description);

    PluginItem setID(int id);

    PluginItem setMinecraftId(String minecraftId);

    String getName();

    String getDisplayName();

    String getDescription();

    int getID();

    String getMinecraftId();


    ItemStack getItemStack();

    // Characteristics

    PluginItem setModelId(int modelId);

    PluginItem setCustomBlockId(int customBlockId);

    PluginItem setUsable(boolean usable);

    PluginItem setEquipable(boolean equipable);

    PluginItem setDroppable(boolean droppable);

    PluginItem setStackSize(int stackSize);

    PluginItem setTransportable(boolean transportable);


    int getModelId();

    int getCustomBlockId();

    boolean isUsable();

    boolean isEquipable();

    boolean isDroppable();

    int getStackSize();

    boolean isTransportable();

    /*Behaviour type*/
    PluginItem setBehaviour(ItemBehaviour behaviour);

    ItemBehaviour getBehaviour();


}
