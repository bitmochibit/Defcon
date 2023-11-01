package com.enderryno.nuclearcraft.utils

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.enums.BlockDataKey
import com.enderryno.nuclearcraft.enums.ItemDataKey
import com.jeff_media.customblockdata.CustomBlockData
import it.unimi.dsi.fastutil.booleans.Boolean2LongFunction
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import kotlin.reflect.KClass

object MetaManager {
    inline fun <reified T> getBlockData(location: Location, key: BlockDataKey): T? {
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)

        // Get the PersistentDataType from the function type
        val dataType = getPersistentDataType(T::class)
        val value: Any = blockData.get(key.key, dataType) ?: return null

        return value as T
    }

    inline fun <reified T : Any> setBlockData(blockData: PersistentDataContainer, key: BlockDataKey, value: T): PersistentDataContainer {

        if (T::class == Boolean::class) {
            blockData.set(key.key, PersistentDataType.BYTE, if (value as Boolean) 1 else 0)
            return blockData
        }
        // Get the PersistentDataType from the function type
        @Suppress("UNCHECKED_CAST")
        val dataType = getPersistentDataType(T::class) as? PersistentDataType<T, T> ?: return blockData

        blockData.set(key.key, dataType, value)
        return blockData
    }

    inline fun <reified T: Any> setBlockData(location: Location, key: BlockDataKey, value: T): PersistentDataContainer {
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)

        return setBlockData(blockData, key, value)
    }

    fun removeBlockData(location: Location, key: BlockDataKey): PersistentDataContainer {
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)

        blockData.remove(key.key)
        return blockData
    }

    inline fun <reified T> getItemData(itemMeta: ItemMeta, key: ItemDataKey): T? {
        val itemData: PersistentDataContainer = itemMeta.persistentDataContainer
        val dataType = getPersistentDataType(T::class)

        val value: Any = itemData.get(key.key, dataType) ?: return null

        return value as T
    }

    inline fun <reified T : Any> setItemData(itemMeta: ItemMeta, key: ItemDataKey, value: T): ItemMeta {
        val itemData: PersistentDataContainer = itemMeta.persistentDataContainer

        if (T::class == Boolean::class) {
            itemData.set(key.key, PersistentDataType.BYTE, if (value as Boolean) 1 else 0)
            return itemMeta
        }

        @Suppress("UNCHECKED_CAST")
        val dataType = getPersistentDataType(T::class) as? PersistentDataType<T, T> ?: return itemMeta

        itemData.set(key.key, dataType, value)
        return itemMeta
    }

    inline fun <reified T: Any> setItemData(itemStack: ItemStack, key: ItemDataKey, value: T): ItemStack {
        val itemMeta = itemStack.itemMeta
        setItemData(itemMeta, key, value)
        return itemStack
    }

    fun removeItemData(itemMeta: ItemMeta, key: ItemDataKey): ItemMeta {
        val itemData: PersistentDataContainer = itemMeta.persistentDataContainer

        itemData.remove(key.key)
        return itemMeta
    }


    fun getPersistentDataType(type: KClass<*>): PersistentDataType<*,*> {
        return when(type) {
            String::class -> PersistentDataType.STRING
            Int::class -> PersistentDataType.INTEGER
            Integer::class -> PersistentDataType.INTEGER
            Double::class -> PersistentDataType.DOUBLE
            Float::class -> PersistentDataType.FLOAT
            Long::class -> PersistentDataType.LONG
            Short::class -> PersistentDataType.SHORT
            Byte::class -> PersistentDataType.BYTE
            Boolean::class -> PersistentDataType.BYTE
            else -> throw IllegalArgumentException("Type not supported, $type")
        }
    }





}