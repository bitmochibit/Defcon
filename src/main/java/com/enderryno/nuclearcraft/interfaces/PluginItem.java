package com.enderryno.nuclearcraft.interfaces;

import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import org.bukkit.inventory.ItemStack;

public interface PluginItem {

    // Base properties

    PluginItem setName(String name);

    PluginItem setDescription(String description);

    PluginItem setID(String id);

    PluginItem setMinecraftId(String minecraftId);

    String getName();

    String getDisplayName();

    String getDescription();

    String getID();

    String getMinecraftId();


    ItemStack getItemStack();

    // Characteristics

    PluginItem setModelId(int modelId);

    PluginItem setCustomBlockId(String customBlockId);

    PluginItem setUsable(boolean usable);

    PluginItem setEquipable(boolean equipable);

    PluginItem setDroppable(boolean droppable);

    PluginItem setStackSize(int stackSize);

    PluginItem setTransportable(boolean transportable);


    int getModelId();

    String getCustomBlockId();

    boolean isUsable();

    boolean isEquipable();

    boolean isDroppable();

    int getStackSize();

    boolean isTransportable();

    /*Behaviour type*/
    PluginItem setBehaviour(ItemBehaviour behaviour);

    ItemBehaviour getBehaviour();


}
