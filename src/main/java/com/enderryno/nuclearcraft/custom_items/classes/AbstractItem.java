package com.enderryno.nuclearcraft.custom_items.classes;


import com.enderryno.nuclearcraft.custom_items.interfaces.GenericItem;
import com.enderryno.nuclearcraft.NuclearCraft;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
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
public class AbstractItem implements GenericItem {

    /*Getting instance of the minecraft plugin (in theory O(1) complexity) */
    private final JavaPlugin plugin = JavaPlugin.getPlugin(NuclearCraft.class);

    private String name;
    private String description;
    private int id;
    private boolean usable;
    private boolean equipable;
    private boolean droppable;

    private int stackSize;
    private boolean transportable;


    private int modelId;
    private int customBlockId;
    private Listener listener;

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
        itemMeta.setDisplayName(this.name);
        itemMeta.setLore(Arrays.asList(this.description.split("\n")));


        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "id"), PersistentDataType.INTEGER, this.id);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "max-item-stack"), PersistentDataType.INTEGER, this.stackSize);

        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "usable"), PersistentDataType.BYTE, this.usable ? (byte) 1 : (byte) 0);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "equipable"), PersistentDataType.BYTE, this.equipable ? (byte) 1 : (byte) 0);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "droppable"), PersistentDataType.BYTE, this.droppable ? (byte) 1 : (byte) 0);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "transportable"), PersistentDataType.BYTE, this.transportable ? (byte) 1 : (byte) 0);


        itemMeta.setCustomModelData(this.modelId);

        customItem.setItemMeta(itemMeta);
        /* Properties assignment */


        return customItem;
    }





    /* Setters/Getters */

    @Override
    public GenericItem setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public GenericItem setMinecraftId(String minecraftId) {
        this.minecraftId = minecraftId;
        return this;
    }

    @Override
    public GenericItem setDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public GenericItem setID(int id) {
        this.id = id;
        return this;
    }

    @Override
    public int getID() {
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
    public String getDescription() {
        return this.description;
    }



    /*Characteristic properties*/

    @Override
    public GenericItem setModelId(int modelId) {
        this.modelId = modelId;
        return this;
    }

    @Override
    public GenericItem setCustomBlockId(int customBlockId) {
        this.customBlockId = customBlockId;
        return this;
    }

    @Override
    public GenericItem setUsable(boolean usable) {
        this.usable = usable;
        return this;
    }

    @Override
    public GenericItem setEquipable(boolean equipable) {
        this.equipable = equipable;
        return this;
    }

    @Override
    public GenericItem setDroppable(boolean droppable) {
        this.droppable = droppable;
        return this;
    }

    @Override
    public GenericItem setStackSize(int stackSize) {
        this.stackSize = stackSize;
        return this;
    }

    @Override
    public GenericItem setTransportable(boolean transportable) {
        this.transportable = transportable;
        return this;
    }

    @Override
    public int getModelId() {
        return this.modelId;
    }

    @Override
    public int getCustomBlockId() {
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


    /* Event Listener */

    @Override
    public GenericItem setEventListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public Listener getEventListener() {
        return this.listener;
    }


}
