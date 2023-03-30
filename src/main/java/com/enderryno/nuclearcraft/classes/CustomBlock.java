package com.enderryno.nuclearcraft.classes;


import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.database.definitions.BlockTable;
import com.enderryno.nuclearcraft.enums.BlockBehaviour;
import com.enderryno.nuclearcraft.interfaces.PluginBlock;
import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import com.enderryno.nuclearcraft.services.BlockRegister;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.sql.ResultSet;


/**
 * This class instantiates a custom item by its id
 * <p>
 * Since it's unsafe to explicitly extend ItemStack class,
 * this class has a getter for both the ItemStack instance and this plugin item class.
 */
public class CustomBlock implements PluginBlock {


    @Override
    public PluginBlock setID(int id) {
        return null;
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public PluginBlock setMinecraftId(String minecraftId) {
        return null;
    }

    @Override
    public String getMinecraftId() {
        return null;
    }

    @Override
    public PluginBlock setCustomBlockId(int customBlockId) {
        return null;
    }

    @Override
    public int getCustomBlockId() {
        return 0;
    }

    @Override
    public void placeBlock(PluginItem item, Location location) {
        // Get block at location
        Block block = location.getWorld().getBlockAt(location);
        // Save metadata for cache and to database
        BlockState state = block.getState();

        state.setMetadata("custom-block-id", new FixedMetadataValue(NuclearCraft.instance, this.getCustomBlockId()));
        state.setMetadata("custom-item-id", new FixedMetadataValue(NuclearCraft.instance, item.getID()));
        state.update();
        // Save to database
        BlockTable blockTable = new BlockTable();
        blockTable.insert(String.valueOf(this.getCustomBlockId()),
                String.valueOf(item.getID()),
                location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
    }

    @Override
    public PluginBlock getBlock(Location location) {
        // Try to get block from cache, if not found, get from database
        Block block = location.getWorld().getBlockAt(location);
        BlockState state = block.getState();
        Integer customBlockId = null;
        if (state.hasMetadata("custom-block-id")) {
            MetadataValue metadataValue = state.getMetadata("custom-block-id").get(0);
            customBlockId = metadataValue.asInt();
        } else {
            // Get from database
            BlockTable blockTable = new BlockTable();
            ResultSet result = blockTable.get(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getName());
            try {
                if (result.next()) {
                    customBlockId = result.getInt("custom_block_id");
                } else {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        // Get custom block from id
        try {
            BlockRegister.getRegisteredBlocks().get(customBlockId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void removeBlock(Location location) {
        // Get block at location
        Block block = location.getWorld().getBlockAt(location);
        // Save metadata for cache and to database
        BlockState state = block.getState();

        state.removeMetadata("custom-block-id", NuclearCraft.instance);
        state.removeMetadata("custom-item-id", NuclearCraft.instance);
        state.update();
        // Save to database
        BlockTable blockTable = new BlockTable();
        blockTable.delete(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getWorld().getName());

    }

    @Override
    public PluginBlock setBehaviour(BlockBehaviour behaviour) {
        return null;
    }

    @Override
    public BlockBehaviour getBehaviour() {
        return null;
    }
}
