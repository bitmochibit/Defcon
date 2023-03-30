package com.enderryno.nuclearcraft.events.blocks;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.classes.CustomItem;
import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.services.BlockRegister;
import com.enderryno.nuclearcraft.services.ItemRegister;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomBlockPlaceEvent implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Get the item in the player's hand
        ItemStack item = event.getItemInHand();
        PluginItem customItem = null;
        Block block = event.getBlock();

        // Get persistent data container
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        // Get key and check if namespace exists
        NamespacedKey blockIdKey = new NamespacedKey(NuclearCraft.getPlugin(NuclearCraft.class), "custom-block-id");
        NamespacedKey itemIdKey = new NamespacedKey(NuclearCraft.getPlugin(NuclearCraft.class), "item-id");
        if (!container.has(blockIdKey, PersistentDataType.STRING)) return;
        if (!container.has(itemIdKey, PersistentDataType.STRING)) return;

        // Get the custom block id and set it to the block
        String customBlockId = container.get(blockIdKey, PersistentDataType.STRING);
        String customItemId = container.get(itemIdKey, PersistentDataType.STRING);

        try {
             customItem = ItemRegister.getRegisteredItems().get(customItemId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            BlockRegister.getRegisteredBlocks()
                    .get(customBlockId)
                    .placeBlock(customItem, block.getLocation());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
