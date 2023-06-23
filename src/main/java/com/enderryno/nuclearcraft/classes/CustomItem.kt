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
class CustomItem(
    override val id: String,
    override val name: String,
    override val description: String?,
    override val minecraftId: String,
    override val modelId: Int,
    override val customBlockId: String?,
    override val isUsable: Boolean,
    override val isEquipable: Boolean,
    override val isDroppable: Boolean,
    override val stackSize: Int,
    override val isTransportable: Boolean,
    override val behaviour: ItemBehaviour
) : PluginItem {
    /*Getting instance of the minecraft plugin (in theory O(1) complexity) */
    private val plugin: JavaPlugin = JavaPlugin.getPlugin(NuclearCraft::class.java)

    override val displayName : String
        get() {
            // Strip color codes from the name
            return ColorParser.stripColor(name)
        }

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
}
