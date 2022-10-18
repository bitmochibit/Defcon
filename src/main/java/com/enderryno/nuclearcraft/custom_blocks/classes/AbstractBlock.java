package com.enderryno.nuclearcraft.custom_blocks.classes;


import com.enderryno.nuclearcraft.custom_blocks.interfaces.GenericBlock;
import com.enderryno.nuclearcraft.custom_items.interfaces.GenericItem;
import com.enderryno.nuclearcraft.custom_items.register.enums.ItemBehaviour;

/**
 * This class instantiates a custom item by its id
 * <p>
 * Since it's unsafe to explicitly extend ItemStack class,
 * this class has a getter for both the ItemStack instance and this plugin item class.
 */
public class AbstractBlock implements GenericBlock {


    @Override
    public GenericItem setID(int id) {
        return null;
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public GenericItem setMinecraftId(String minecraftId) {
        return null;
    }

    @Override
    public String getMinecraftId() {
        return null;
    }

    @Override
    public GenericItem setCustomBlockId(int customBlockId) {
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
    public GenericItem setBehaviour(ItemBehaviour behaviour) {
        return null;
    }

    @Override
    public ItemBehaviour getBehaviour() {
        return null;
    }
}
