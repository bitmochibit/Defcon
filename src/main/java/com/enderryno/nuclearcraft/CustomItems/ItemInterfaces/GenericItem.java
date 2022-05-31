package com.enderryno.nuclearcraft.CustomItems.ItemInterfaces;

import org.bukkit.event.Listener;

public interface GenericItem {

    // Base properties

    public GenericItem setName(String name);
    public GenericItem setDescription(String description);
    public GenericItem setID(int id);
    public GenericItem setMinecraftId(String minecraftId);

    public String getName();
    public String getDescription();
    public int getID();
    public String getMinecraftId();



    // Characteristics

    public GenericItem setModelId(int modelId);
    public GenericItem setCustomBlockId(int customBlockId);

    public GenericItem setUsable(boolean usable);
    public GenericItem setEquipable(boolean equipable);
    public GenericItem setDroppable(boolean droppable);
    public GenericItem setStackSize(int stackSize);
    public GenericItem setTransportable(boolean transportable);


    public int getModelId();
    public int getCustomBlockId();

    public boolean isUsable();
    public boolean isEquipable();
    public boolean isDroppable();
    public int getStackSize();
    public boolean isTransportable();

    /* Functional event listener class */
    public GenericItem setEventListener(Listener listener);
    public Listener getEventListener();


}
