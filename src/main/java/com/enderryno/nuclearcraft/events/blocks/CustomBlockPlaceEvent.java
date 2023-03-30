package com.enderryno.nuclearcraft.events.blocks;

import com.enderryno.nuclearcraft.NuclearCraft;
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
        Block block = event.getBlock();
        TileState tileState = (TileState) block.getState();


        // Get persistent data container
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        // Get key and check if namespace exists
        NamespacedKey key = new NamespacedKey(NuclearCraft.getPlugin(NuclearCraft.class), "custom-block-id");
        if (!container.has(key, PersistentDataType.STRING)) return;

        // Get the custom block id and set it to the block
        String customBlockId = container.get(key, PersistentDataType.STRING);
        tileState.getPersistentDataContainer().set(key, PersistentDataType.STRING, customBlockId);

        tileState.update();
        event.getPlayer().sendMessage("Custom block id placed: " + customBlockId);

    }
}
