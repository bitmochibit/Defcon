package com.enderryno.nuclearcraft.classes;


import com.enderryno.nuclearcraft.interfaces.PluginBlock;
import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;

/**
 * This class instantiates a custom item by its id
 * <p>
 * Since it's unsafe to explicitly extend ItemStack class,
 * this class has a getter for both the ItemStack instance and this plugin item class.
 */
public class CustomBlock implements PluginBlock {


    @Override
    public PluginItem setID(int id) {
        return null;
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public PluginItem setMinecraftId(String minecraftId) {
        return null;
    }

    @Override
    public String getMinecraftId() {
        return null;
    }

    @Override
    public PluginItem setCustomBlockId(int customBlockId) {
        return null;
    }

    @Override
    public int getCustomBlockId() {
        return 0;
    }

    @Override
    public void placeBlock(double x, double y, double z) {

    }

    @Override
    public void removeBlock(double x, double y, double z) {

    }

    @Override
    public PluginItem setBehaviour(ItemBehaviour behaviour) {
        return null;
    }

    @Override
    public ItemBehaviour getBehaviour() {
        return null;
    }
}
