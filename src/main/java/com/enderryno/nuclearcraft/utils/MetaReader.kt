package com.enderryno.nuclearcraft.utils

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.enums.BlockDataKey
import com.enderryno.nuclearcraft.enums.ItemDataKey
import com.jeff_media.customblockdata.CustomBlockData
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

import javax.lang.model.type.PrimitiveType

object MetaReader {
    inline fun <reified T> getBlockData(location: Location, key: BlockDataKey): T? {
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, NuclearCraft.instance!!)

        // Get the PersistentDataType from the function type
        val dataType = getPersistentDataType(T::class.java)
        val value: Any = blockData.get(key.key, dataType) ?: return null

        return value as T
    }

    inline fun <reified T> getItemData(item: ItemStack, key: ItemDataKey): T? {
        val itemMeta = item.itemMeta?: return null

        val itemData: PersistentDataContainer = itemMeta.persistentDataContainer
        val dataType = getPersistentDataType(T::class.java)

        val value: Any = itemData.get(key.key, dataType) ?: return null

        return value as T
    }

    fun <T> getPersistentDataType(type: T): PersistentDataType<out Any, out Any> {
        return when(type) {
            is String -> PersistentDataType.STRING
            is Int -> PersistentDataType.INTEGER
            is Double -> PersistentDataType.DOUBLE
            is Float -> PersistentDataType.FLOAT
            is Long -> PersistentDataType.LONG
            is Short -> PersistentDataType.SHORT
            is Byte -> PersistentDataType.BYTE
            is Boolean -> PersistentDataType.BYTE
            else -> throw IllegalArgumentException("Type not supported")
        }
    }





}