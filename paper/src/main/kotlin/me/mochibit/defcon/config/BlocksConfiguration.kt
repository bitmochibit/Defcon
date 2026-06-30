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

package me.mochibit.defcon.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.mochibit.defcon.content.blocks.BlockBehaviour
import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.blocks.PluginBlockProperties
import me.mochibit.defcon.content.element.ElementBehaviour
import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.utils.Logger

object BlocksConfiguration : PluginConfiguration<BlocksConfiguration.BlocksConfig>("blocks") {
    @Serializable
    data class BlocksConfig(
        val blocks: List<BlockDefinitionJson> = emptyList(),
    )

    @Serializable
    data class BlockDefinitionJson(
        val id: String,
        @SerialName("block-basis")
        val blockBasis: String = "minecraft:stone",
        val behaviour: String,
        val properties: Map<String, JsonElement> = emptyMap(),
    )

    data class BlockDefinition(
        override val id: String,
        val blockBasis: String = "minecraft:stone",
        override val behaviour: ElementBehaviour<PluginBlockProperties, PluginBlock<*>>,
        override val behaviourData: Map<String, Any>,
    ) : ElementDefinition<PluginBlockProperties, PluginBlock<*>>

    override suspend fun loadSchema(): BlocksConfig {
        val configText = readConfigFile()
        return json.decodeFromString<BlocksConfig>(configText)
    }

    override fun getDefaultSchema(): BlocksConfig = BlocksConfig()

    override fun getSerializer(): KSerializer<BlocksConfig> = BlocksConfig.serializer()

    override suspend fun cleanupSchema() {}

    // Helper method to get parsed block definitions
    suspend fun getBlockDefinitions(): List<BlockDefinition> {
        val config = getSchema()
        return config.blocks.mapNotNull { blockJson ->
            parseBlockDefinition(blockJson)
        }
    }

    private fun parseBlockDefinition(blockJson: BlockDefinitionJson): BlockDefinition? {
        val behaviour =
            try {
                BlockBehaviour.valueOf(blockJson.behaviour.uppercase())
            } catch (_: IllegalArgumentException) {
                Logger.warn("Block ${blockJson.id} has invalid behaviour '${blockJson.behaviour}', skipping")
                return null
            }

        val behaviourData = mutableMapOf<String, Any>()
        blockJson.properties.forEach { (key, jsonElement) ->
            behaviourData[key] =
                when {
                    jsonElement is kotlinx.serialization.json.JsonPrimitive -> {
                        when {
                            jsonElement.isString -> {
                                jsonElement.content
                            }

                            else -> {
                                jsonElement.content.toLongOrNull()
                                    ?: jsonElement.content.toDoubleOrNull()
                                    ?: jsonElement.content.toBooleanStrictOrNull()
                                    ?: jsonElement.content
                            }
                        }
                    }

                    else -> {
                        jsonElement.toString()
                    }
                }
        }

        return BlockDefinition(
            id = blockJson.id,
            blockBasis = blockJson.blockBasis,
            behaviour = behaviour,
            behaviourData = behaviourData,
        )
    }
}
