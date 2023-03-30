package com.enderryno.nuclearcraft.classes;


import com.enderryno.nuclearcraft.interfaces.PluginItem;
import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.enums.ItemBehaviour;
import com.enderryno.nuclearcraft.utils.ColorParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * This class instantiates a custom item by its id
 * <p>
 * Since it's unsafe to explicitly extend ItemStack class,
 * this class has a getter for both the ItemStack instance and this plugin item class.
 */
public class CustomItem implements PluginItem {

    /*Getting instance of the minecraft plugin (in theory O(1) complexity) */
    private final JavaPlugin plugin = JavaPlugin.getPlugin(NuclearCraft.class);

    private String name;
    private String description;
    private String id;
    private boolean usable;
    private boolean equipable;
    private boolean droppable;

    private int stackSize;
    private boolean transportable;


    private int modelId;
    private String customBlockId;
    private ItemBehaviour behaviour;

    private String minecraftId;


    /* Instantiation */
    @SuppressWarnings("deprecation")
    // Suppressing deprecation warning for the ItemStack constructor (Paper api is slightly different)
    public ItemStack getItemStack() {
        Material material = Material.getMaterial(this.minecraftId);
        if (material == null) {
            throw new IllegalArgumentException("Material " + this.minecraftId + " does not exist");
        }
        ItemStack customItem = new ItemStack(material);

        /* Meta assignment */
        ItemMeta itemMeta = customItem.getItemMeta();
        itemMeta.setDisplayName(ColorParser.parseColor(this.name));
        itemMeta.setLore(ColorParser.parseColor(Arrays.asList(this.description.split("\n"))));

        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item-id"), PersistentDataType.STRING, this.id);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "max-item-stack"), PersistentDataType.INTEGER, this.stackSize);

        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "usable"), PersistentDataType.BYTE, this.usable ? (byte) 1 : (byte) 0);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "equipable"), PersistentDataType.BYTE, this.equipable ? (byte) 1 : (byte) 0);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "droppable"), PersistentDataType.BYTE, this.droppable ? (byte) 1 : (byte) 0);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "transportable"), PersistentDataType.BYTE, this.transportable ? (byte) 1 : (byte) 0);

        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "item-behaviour"), PersistentDataType.STRING, this.behaviour.getName());

        if (this.customBlockId != null) {
            itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom-block-id"), PersistentDataType.STRING, this.customBlockId);
        }

        itemMeta.setCustomModelData(this.modelId);

        customItem.setItemMeta(itemMeta);
        /* Properties assignment */

        return customItem;
    }





    /* Setters/Getters */

    @Override
    public PluginItem setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public PluginItem setMinecraftId(String minecraftId) {
        this.minecraftId = minecraftId;
        return this;
    }

    @Override
    public PluginItem setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public PluginItem setID(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String getID() {
        return this.id;
    }

    @Override
    public String getMinecraftId() {
        return this.minecraftId;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return ColorParser.parseColor(this.name);
    }


    @Override
    public String getDescription() {
        return this.description;
    }



    /*Characteristic properties*/

    @Override
    public PluginItem setModelId(int modelId) {
        this.modelId = modelId;
        return this;
    }

    @Override
    public PluginItem setCustomBlockId(String customBlockId) {
        this.customBlockId = customBlockId;
        return this;
    }

    @Override
    public PluginItem setUsable(boolean usable) {
        this.usable = usable;
        return this;
    }

    @Override
    public PluginItem setEquipable(boolean equipable) {
        this.equipable = equipable;
        return this;
    }

    @Override
    public PluginItem setDroppable(boolean droppable) {
        this.droppable = droppable;
        return this;
    }

    @Override
    public PluginItem setStackSize(int stackSize) {
        this.stackSize = stackSize;
        return this;
    }

    @Override
    public PluginItem setTransportable(boolean transportable) {
        this.transportable = transportable;
        return this;
    }

    @Override
    public int getModelId() {
        return this.modelId;
    }

    @Override
    public String getCustomBlockId() {
        return this.customBlockId;
    }

    @Override
    public boolean isUsable() {
        return this.usable;
    }

    @Override
    public boolean isEquipable() {
        return this.equipable;
    }

    @Override
    public boolean isDroppable() {
        return this.droppable;
    }

    @Override
    public int getStackSize() {
        return this.stackSize;
    }

    @Override
    public boolean isTransportable() {
        return this.transportable;
    }

    /*Behaviours*/

    @Override
    public PluginItem setBehaviour(ItemBehaviour behaviour) {
        this.behaviour = behaviour;
        return this;
    }

    @Override
    public ItemBehaviour getBehaviour() {
        return this.behaviour;
    }


}
