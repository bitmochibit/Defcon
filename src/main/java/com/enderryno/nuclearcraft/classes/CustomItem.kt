package com.enderryno.nuclearcraft.classes

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.enums.ItemBehaviour
import com.enderryno.nuclearcraft.interfaces.PluginItem
import com.enderryno.nuclearcraft.utils.ColorParser
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * This class instantiates a custom item by its id
 *
 *
 * Since it's unsafe to explicitly extend ItemStack class,
 * this class has a getter for both the ItemStack instance and this plugin item class.
 */
class CustomItem : PluginItem {
    /*Getting instance of the minecraft plugin (in theory O(1) complexity) */
    private val plugin: JavaPlugin = JavaPlugin.getPlugin(NuclearCraft::class.java)
    override var name: String? = null
        private set
    override var description: String? = null
        private set
    override var id: String? = null
        private set
    override var isUsable = false
        private set
    override var isEquipable = false
        private set
    override var isDroppable = false
        private set
    override var stackSize = 0
        private set
    override var isTransportable = false
        private set
    override var modelId = 0
        private set
    override var customBlockId: String? = null
        private set
    override var behaviour: ItemBehaviour? = null
        private set
    override var minecraftId: String? = null
        private set

    override val itemStack: ItemStack
        /* Instantiation */ // Suppressing deprecation warning for the ItemStack constructor (Paper api is slightly different)
        get() {
            val material = Material.getMaterial(minecraftId!!)
                    ?: throw IllegalArgumentException("Material $minecraftId does not exist")
            val customItem = ItemStack(material)

            /* Meta assignment */
            val itemMeta = customItem.itemMeta
            itemMeta.setDisplayName(ColorParser.parseColor(name))
            itemMeta.lore = ColorParser.parseColor(Arrays.asList(*description!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "item-id"), PersistentDataType.STRING, id!!)
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "max-item-stack"), PersistentDataType.INTEGER, stackSize)
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "usable"), PersistentDataType.BYTE, if (isUsable) 1.toByte() else 0.toByte())
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "equipable"), PersistentDataType.BYTE, if (isEquipable) 1.toByte() else 0.toByte())
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "droppable"), PersistentDataType.BYTE, if (isDroppable) 1.toByte() else 0.toByte())
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "transportable"), PersistentDataType.BYTE, if (isTransportable) 1.toByte() else 0.toByte())
            itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "item-behaviour"), PersistentDataType.STRING, behaviour?.name!!)
            if (customBlockId != null) {
                itemMeta.persistentDataContainer.set(NamespacedKey(plugin, "custom-block-id"), PersistentDataType.STRING, customBlockId!!)
            }
            itemMeta.setCustomModelData(modelId)
            customItem.setItemMeta(itemMeta)
            /* Properties assignment */return customItem
        }

    /* Setters/Getters */
    override fun setName(name: String?): PluginItem {
        this.name = name
        return this
    }

    override fun setMinecraftId(minecraftId: String?): PluginItem {
        this.minecraftId = minecraftId
        return this
    }

    override fun setDescription(description: String?): PluginItem {
        this.description = description
        return this
    }

    override fun setID(id: String?): PluginItem {
        this.id = id
        return this
    }

    override val displayName: String?
        get() = ColorParser.parseColor(name)

    /*Characteristic properties*/
    override fun setModelId(modelId: Int): PluginItem {
        this.modelId = modelId
        return this
    }

    override fun setCustomBlockId(customBlockId: String?): PluginItem {
        this.customBlockId = customBlockId
        return this
    }

    override fun setUsable(usable: Boolean): PluginItem {
        isUsable = usable
        return this
    }

    override fun setEquipable(equipable: Boolean): PluginItem {
        isEquipable = equipable
        return this
    }

    override fun setDroppable(droppable: Boolean): PluginItem {
        isDroppable = droppable
        return this
    }

    override fun setStackSize(stackSize: Int): PluginItem {
        this.stackSize = stackSize
        return this
    }

    override fun setTransportable(transportable: Boolean): PluginItem {
        isTransportable = transportable
        return this
    }

    /*Behaviours*/
    override fun setBehaviour(behaviour: ItemBehaviour?): PluginItem {
        this.behaviour = behaviour
        return this
    }
}
