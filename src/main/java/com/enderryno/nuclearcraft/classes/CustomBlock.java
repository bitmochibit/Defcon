package com.enderryno.nuclearcraft.classes;


import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.database.definitions.BlockTable;
import com.enderryno.nuclearcraft.enums.BlockBehaviour;
import com.enderryno.nuclearcraft.interfaces.PluginBlock;
import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import com.enderryno.nuclearcraft.services.BlockRegister;
import com.jeff_media.customblockdata.CustomBlockData;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;


/**
 * This class instantiates a custom item by its id
 * <p>
 * Since it's unsafe to explicitly extend ItemStack class,
 * this class has a getter for both the ItemStack instance and this plugin item class.
 */
public class CustomBlock implements PluginBlock {
    private String id;
    private String minecraftId;
    private BlockBehaviour behaviour;

    int customModelId;

    @Override
    public PluginBlock setID(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String getID() {
        return this.id;
    }

    @Override
    public PluginBlock setCustomModelId(int customModelId) {
        this.customModelId = customModelId;
        return this;
    }

    @Override
    public int getCustomModelId() {
        return this.customModelId;
    }

    @Override
    public PluginBlock setMinecraftId(String minecraftId) {
        this.minecraftId = minecraftId;
        return this;
    }

    @Override
    public String getMinecraftId() {
        return this.minecraftId;
    }


    @Override
    public void placeBlock(PluginItem item, Location location) {
        // Get block at location
        Block block = location.getWorld().getBlockAt(location);
        // Save metadata
        PersistentDataContainer blockData = new CustomBlockData(block, NuclearCraft.instance);

        NamespacedKey blockIdKey = new NamespacedKey(NuclearCraft.instance, "custom-block-id");
        NamespacedKey itemIdKey = new NamespacedKey(NuclearCraft.instance, "item-id");

        blockData.set(blockIdKey, PersistentDataType.STRING, this.getID());
        blockData.set(itemIdKey, PersistentDataType.STRING, item.getID());

        // Print in chat for debugging
        NuclearCraft.instance.getLogger().info("Placed block " + blockData.get(blockIdKey, PersistentDataType.STRING));
    }

    @Override
    public PluginBlock getBlock(Location location) {
        // Try to get block metadata
        Block block = location.getWorld().getBlockAt(location);
        NamespacedKey blockIdKey = new NamespacedKey(NuclearCraft.instance, "custom-block-id");
        PersistentDataContainer blockData = new CustomBlockData(block, NuclearCraft.instance);
        String customBlockId = blockData.get(blockIdKey, PersistentDataType.STRING);
        try {
            BlockRegister.getRegisteredBlocks().get(customBlockId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    @Override
    public void removeBlock(Location location) {
        // Get block at location
        Block block = location.getWorld().getBlockAt(location);
        PersistentDataContainer blockData = new CustomBlockData(block, NuclearCraft.instance);

        NamespacedKey blockIdKey = new NamespacedKey(NuclearCraft.instance, "custom-block-id");
        NamespacedKey itemIdKey = new NamespacedKey(NuclearCraft.instance, "item-id");

        blockData.remove(blockIdKey);
        blockData.remove(itemIdKey);
    }

    @Override
    public PluginBlock setBehaviour(BlockBehaviour behaviour) {
        this.behaviour = behaviour;
        return this;
    }

    @Override
    public BlockBehaviour getBehaviour() {
        return this.behaviour;
    }
}
