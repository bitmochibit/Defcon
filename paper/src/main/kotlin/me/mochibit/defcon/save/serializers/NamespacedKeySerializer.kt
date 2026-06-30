/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2026 mochibit.
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

package me.mochibit.defcon.save.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.NamespacedKey

/**
 * Custom serializer for Bukkit's NamespacedKey
 */
object NamespacedKeySerializer : KSerializer<NamespacedKey> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NamespacedKey", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: NamespacedKey,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): NamespacedKey {
        val keyString = decoder.decodeString()
        return NamespacedKey.fromString(keyString)
            ?: throw IllegalArgumentException("Invalid NamespacedKey format: $keyString")
    }
}
