/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.utils

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.enums.BlockDataKey
import me.mochibit.defcon.enums.ItemDataKey
import com.jeff_media.customblockdata.CustomBlockData
import me.mochibit.defcon.extensions.toBoolean
import me.mochibit.defcon.extensions.toByte
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import kotlin.reflect.KClass

object MetaManager {
    inline fun <reified T> getBlockData(location: Location, key: BlockDataKey): T? {
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, Defcon.instance)

        // Get the PersistentDataType from the function type
        val dataType = getPersistentDataType(T::class)
        val value: Any = blockData.get(key.key, dataType) ?: return null

        // if the type is boolean, we need to convert the value to boolean
        if (T::class == Boolean::class) {
            return (value as Byte).toBoolean() as T
        }

        return value as T
    }

    inline fun <reified T : Any> setBlockData(blockData: PersistentDataContainer, key: BlockDataKey, value: T): PersistentDataContainer {
        // If the type is boolean, we need to convert value to byte
        if (T::class == Boolean::class) {
            blockData.set(key.key, PersistentDataType.BYTE, (value as Boolean).toByte())
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
        val blockData: PersistentDataContainer = CustomBlockData(block, Defcon.instance)

        return setBlockData(blockData, key, value)
    }

    fun removeBlockData(location: Location, key: BlockDataKey): PersistentDataContainer {
        val block = location.world.getBlockAt(location)
        val blockData: PersistentDataContainer = CustomBlockData(block, Defcon.instance)

        blockData.remove(key.key)
        return blockData
    }

    inline fun <reified T> getItemData(itemMeta: ItemMeta, key: ItemDataKey): T? {
        val itemData: PersistentDataContainer = itemMeta.persistentDataContainer
        val dataType = getPersistentDataType(T::class)

        val value: Any = itemData.get(key.key, dataType) ?: return null

        // if the type is boolean, we need to convert the value to boolean
        if (T::class == Boolean::class) {
            return (value as Byte).toBoolean() as T
        }

        return value as T
    }

    inline fun <reified T : Any> setItemData(itemMeta: ItemMeta, key: ItemDataKey, value: T): ItemMeta {
        val itemData: PersistentDataContainer = itemMeta.persistentDataContainer

        // If the type is boolean, we need to convert value to byte
        if (T::class == Boolean::class) {
            itemData.set(key.key, PersistentDataType.BYTE, (value as Boolean).toByte())
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

    fun convertStringToNamespacedKey(string: String): NamespacedKey {
        // Split the string into the namespace and key, if the string has no colon, the namespace is "defcon"
        val split = string.split(":")
        val namespace = if (split.size == 1) "defcon" else split[0]
        val key = if (split.size == 1) split[0] else split[1]
        return NamespacedKey(namespace, key)
    }


}