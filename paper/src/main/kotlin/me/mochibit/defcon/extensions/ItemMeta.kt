/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.extensions

import me.mochibit.defcon.pluginNamespacedKey
import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

sealed interface DataProperty<T : Any> {
    val key: NamespacedKey
    val type: PersistentDataType<*, T>
}

@JvmInline
value class StringProperty(
    override val key: NamespacedKey,
) : DataProperty<String> {
    override val type: PersistentDataType<*, String>
        get() = PersistentDataType.STRING
}

@JvmInline
value class IntProperty(
    override val key: NamespacedKey,
) : DataProperty<Int> {
    override val type: PersistentDataType<*, Int>
        get() = PersistentDataType.INTEGER
}

@JvmInline
value class BooleanProperty(
    override val key: NamespacedKey,
) : DataProperty<Boolean> {
    override val type: PersistentDataType<*, Boolean>
        get() = PersistentDataType.BOOLEAN
}

@JvmInline
value class ByteProperty(
    override val key: NamespacedKey,
) : DataProperty<Byte> {
    override val type: PersistentDataType<*, Byte>
        get() = PersistentDataType.BYTE
}

@JvmInline
value class DoubleProperty(
    override val key: NamespacedKey,
) : DataProperty<Double> {
    override val type: PersistentDataType<*, Double>
        get() = PersistentDataType.DOUBLE
}

object PluginItemPropertyKeys {
    val itemId = StringProperty(pluginNamespacedKey("item-id"))
}

fun <T : Any> ItemMeta.setData(
    property: DataProperty<T>,
    value: T,
) {
    this.persistentDataContainer.set(property.key, property.type, value)
}

fun <T : Any> ItemMeta.getData(property: DataProperty<T>): T? = this.persistentDataContainer.get(property.key, property.type)

fun <T : Any> ItemMeta.hasData(property: DataProperty<T>): Boolean = this.persistentDataContainer.has(property.key, property.type)

fun <T : Any> ItemMeta.removeData(property: DataProperty<T>) {
    this.persistentDataContainer.remove(property.key)
}
