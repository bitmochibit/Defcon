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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.content.items.ItemBehaviour
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemProperties
import me.mochibit.defcon.utils.Logger
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot

object ItemsConfiguration : PluginConfiguration<List<ItemsConfiguration.ItemDefinition>>("items") {
    @Serializable
    data class ItemsConfig(
        val items: List<ItemDefinitionJson> = emptyList(),
        @SerialName("block-items")
        val blockItems: List<ItemDefinitionJson> = emptyList(),
    )

    @Serializable
    data class ItemDefinitionJson(
        val id: String,
        @SerialName("display-name")
        val displayName: String = "Unnamed Item",
        val description: String? = null,
        @SerialName("minecraft-id")
        val minecraftId: String? = null,
        @SerialName("legacy-minecraft-id")
        val legacyMinecraftId: String? = null,
        val model: String? = null,
        @SerialName("legacy-model-id")
        val legacyItemModel: Int? = null,
        @SerialName("equip-slot")
        val equipmentSlot: String? = null,
        @SerialName("max-stack-size")
        val maxStackSize: Int = 64,
        val behaviour: String,
        val properties: Map<String, JsonElement> = emptyMap(),
        val crafting: CraftingRecipeJson? = null,
    )

    @Serializable
    data class CraftingRecipeJson(
        val type: String,
        @SerialName("result-amount")
        val resultAmount: Int = 1,
        val pattern: List<String> = emptyList(),
        val key: Map<String, IngredientEntryJson> = emptyMap(),
        val ingredients: List<IngredientEntryJson> = emptyList(),
    )

    @Serializable
    data class IngredientEntryJson(
        val item: String? = null,
        val tag: String? = null,
        val count: Int = 1,
    )

    data class ItemDefinition(
        override val id: String,
        val displayName: String = "Unnamed Item",
        val description: String? = null,
        val minecraftId: String? = null,
        val legacyMinecraftId: String? = minecraftId,
        val itemModel: NamespacedKey? = null,
        val legacyItemModel: Int? = null,
        val equipmentSlot: EquipmentSlot? = null,
        val maxStackSize: Int = 64,
        override val behaviour: ItemBehaviour,
        override val behaviourData: Map<String, Any> = emptyMap(),
        val craftingRecipe: CraftingRecipe? = null,
        val isBlockItem: Boolean = false,
    ) : ElementDefinition<PluginItemProperties, PluginItem<*>> {
        sealed interface CraftingRecipe {
            data class ShapedCraftingRecipe(
                val resultAmount: Int,
                val pattern: List<String>,
                val keys: Map<Char, IngredientEntry>,
            ) : CraftingRecipe

            data class ShapelessCraftingRecipe(
                val resultAmount: Int,
                val ingredients: List<IngredientEntry>,
            ) : CraftingRecipe

            data class IngredientEntry(
                val itemNamespaced: String?,
                val tag: String?,
                val count: Int = 1,
            )
        }
    }

    override suspend fun cleanupSchema() {}

    override suspend fun loadSchema(): List<ItemDefinition> {
        val configText = readConfigFile()
        val config = json.decodeFromString<ItemsConfig>(configText)

        val items = config.items.map { parseItemDefinition(it, false) }
        val blockItems = config.blockItems.map { parseItemDefinition(it, true) }

        return items + blockItems
    }

    private fun parseItemDefinition(
        itemJson: ItemDefinitionJson,
        blockItem: Boolean,
    ): ItemDefinition {
        val itemModel = itemJson.model?.let { NamespacedKey.fromString(it) }

        val equipmentSlot =
            itemJson.equipmentSlot?.let {
                try {
                    EquipmentSlot.valueOf(it.uppercase())
                } catch (_: IllegalArgumentException) {
                    Logger.err("Invalid equipment slot '$it' for item ${itemJson.id}, using null")
                    null
                }
            }

        val itemBehaviour =
            try {
                ItemBehaviour.valueOf(itemJson.behaviour.uppercase())
            } catch (_: IllegalArgumentException) {
                Logger.err("Invalid item behaviour '${itemJson.behaviour}' for item ${itemJson.id}, using default")
                ItemBehaviour.GAS_MASK // Use a safe default instead
            }

        val behaviourData = mutableMapOf<String, Any>()
        itemJson.properties.forEach { (key, jsonElement) ->
            behaviourData[key] =
                when {
                    jsonElement is kotlinx.serialization.json.JsonPrimitive -> {
                        when {
                            jsonElement.isString -> {
                                jsonElement.content
                            }

                            else -> {
                                // Try to parse as number or boolean - try integer types first, then double
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

        val itemRecipe = itemJson.crafting?.let { parseRecipe(it) }

        return ItemDefinition(
            id = itemJson.id,
            displayName = itemJson.displayName,
            description = itemJson.description,
            minecraftId = itemJson.minecraftId,
            legacyMinecraftId = itemJson.legacyMinecraftId ?: itemJson.minecraftId,
            itemModel = itemModel,
            legacyItemModel = itemJson.legacyItemModel,
            equipmentSlot = equipmentSlot,
            maxStackSize = itemJson.maxStackSize,
            behaviour = itemBehaviour,
            behaviourData = behaviourData,
            craftingRecipe = itemRecipe,
            isBlockItem = blockItem,
        )
    }

    private fun parseRecipe(craftingJson: CraftingRecipeJson): ItemDefinition.CraftingRecipe? {
        val craftingType = craftingJson.type.lowercase()
        val resultAmount = craftingJson.resultAmount

        return when (craftingType) {
            "shaped" -> {
                if (craftingJson.pattern.isEmpty()) {
                    Logger.err("Empty pattern for shaped recipe, skipping crafting")
                    return null
                }
                val keys =
                    craftingJson.key
                        .mapValues { (_, ingredientJson) ->
                            ItemDefinition.CraftingRecipe.IngredientEntry(
                                itemNamespaced = ingredientJson.item,
                                tag = ingredientJson.tag,
                                count = ingredientJson.count,
                            )
                        }.mapKeys { it.key.first() }

                ItemDefinition.CraftingRecipe.ShapedCraftingRecipe(resultAmount, craftingJson.pattern, keys)
            }

            "shapeless" -> {
                if (craftingJson.ingredients.isEmpty()) {
                    Logger.err("Empty ingredients list for shapeless recipe, skipping crafting")
                    return null
                }
                val ingredients =
                    craftingJson.ingredients.map { ingredientJson ->
                        ItemDefinition.CraftingRecipe.IngredientEntry(
                            itemNamespaced = ingredientJson.item,
                            tag = ingredientJson.tag,
                            count = ingredientJson.count,
                        )
                    }
                ItemDefinition.CraftingRecipe.ShapelessCraftingRecipe(resultAmount, ingredients)
            }

            else -> {
                Logger.err("Invalid crafting type '$craftingType' for item, skipping crafting")
                null
            }
        }
    }
}
