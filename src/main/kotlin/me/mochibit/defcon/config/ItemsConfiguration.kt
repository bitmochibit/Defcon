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

import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.content.items.ItemBehaviour
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemProperties
import me.mochibit.defcon.utils.Logger
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.EquipmentSlot

object ItemsConfiguration : PluginConfiguration<List<ItemsConfiguration.ItemDefinition>>("items") {

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
    ) : ElementDefinition<PluginItemProperties, PluginItem> {
        sealed interface CraftingRecipe {
            data class ShapedCraftingRecipe(
                val resultAmount: Int, val pattern: List<String>, val keys: Map<Char, IngredientEntry>
            ) : CraftingRecipe

            data class ShapelessCraftingRecipe(
                val resultAmount: Int, val ingredients: List<IngredientEntry>
            ) : CraftingRecipe

            data class IngredientEntry(
                val itemNamespaced: String?, val tag: String?, val count: Int = 1
            )
        }
    }

    override suspend fun cleanupSchema() {}

    override suspend fun loadSchema(): List<ItemDefinition> {
        val tempItems = mutableListOf<ItemDefinition>()

        config.getConfigurationSection("items")?.let { itemsSection ->
            tempItems.addAll(parseItemsFromSection(itemsSection, false))
        }

        config.getConfigurationSection("block-items")?.let { blockItemsSection ->
            tempItems.addAll(parseItemsFromSection(blockItemsSection, true))
        }

        return tempItems.toList()
    }

    private fun parseItemsFromSection(section: ConfigurationSection, isBlockItem: Boolean): List<ItemDefinition> {
        return section.getKeys(false).mapNotNull { id ->
            val itemSection = section.getConfigurationSection(id) ?: return@mapNotNull null
            parseItemDefinition(id, itemSection, isBlockItem)
        }
    }

    private fun parseItemDefinition(
        id: String,
        itemSection: ConfigurationSection,
        blockItem: Boolean
    ): ItemDefinition? {
        val displayName = itemSection.getString("display-name") ?: "Unnamed Item"
        val description = itemSection.getString("description")
        val minecraftId = itemSection.getString("minecraft-id")
        val legacyMinecraftId = itemSection.getString("legacy-minecraft-id") ?: minecraftId

        val itemModel = itemSection.getString("model", null)?.let {
            NamespacedKey.fromString(it)
        }

        val legacyModelId = itemSection.getInt("legacy-model-id", 0)

        val equipmentSlot = itemSection.getString("equip-slot", null)?.let {
            try {
                EquipmentSlot.valueOf(it.uppercase())
            } catch (ex: IllegalArgumentException) {
                Logger.err("Invalid equipment slot '$it' for item $id, using null")
                null
            }
        }

        val maxStackSize = itemSection.getInt("max-stack-size", 64)
        val behaviourValue = itemSection.getString("behaviour") ?: return null

        val itemBehaviour = try {
            ItemBehaviour.valueOf(behaviourValue.uppercase())
        } catch (ex: IllegalArgumentException) {
            Logger.err("Invalid item behaviour '$behaviourValue' for item $id, skipping..")
            return null
        }

        val behaviourData = mutableMapOf<String, Any>()
        itemSection.getConfigurationSection("properties")?.let { propertiesSection ->
            propertiesSection.getKeys(false).forEach { key ->
                behaviourData[key] = propertiesSection.get(key) ?: ""
            }
        }

        val itemRecipe: ItemDefinition.CraftingRecipe? =
            parseRecipe(itemSection.getConfigurationSection("crafting"))

        return ItemDefinition(
            id = id,
            displayName = displayName,
            description = description,
            minecraftId = minecraftId,
            legacyMinecraftId = legacyMinecraftId,
            itemModel = itemModel,
            legacyItemModel = legacyModelId,
            equipmentSlot = equipmentSlot,
            maxStackSize = maxStackSize,
            behaviour = itemBehaviour,
            behaviourData = behaviourData,
            craftingRecipe = itemRecipe,
            isBlockItem = blockItem
        )
    }

    private fun parseRecipe(craftingSection: ConfigurationSection?): ItemDefinition.CraftingRecipe? {
        craftingSection ?: return null

        val craftingType = craftingSection.getString("type") ?: return null
        val resultAmount = craftingSection.getInt("result-amount", 1)

        return when (craftingType.lowercase()) {
            "shaped" -> parseShapedRecipe(craftingSection, resultAmount)
            "shapeless" -> parseShapelessRecipe(craftingSection, resultAmount)
            else -> {
                Logger.err("Invalid crafting type '$craftingType' for item, skipping crafting")
                null
            }
        }
    }

    private fun parseShapedRecipe(
        craftingSection: ConfigurationSection,
        resultAmount: Int
    ): ItemDefinition.CraftingRecipe.ShapedCraftingRecipe? {
        val pattern = craftingSection.getStringList("pattern")
        if (pattern.isEmpty()) {
            Logger.err("Empty pattern for shaped recipe in item, skipping crafting")
            return null
        }

        val keys = parseRecipeKeys(craftingSection.getConfigurationSection("key"))
        return ItemDefinition.CraftingRecipe.ShapedCraftingRecipe(resultAmount, pattern, keys)
    }

    private fun parseShapelessRecipe(
        craftingSection: ConfigurationSection,
        resultAmount: Int
    ): ItemDefinition.CraftingRecipe.ShapelessCraftingRecipe? {
        val ingredientsList = craftingSection.getMapList("ingredients")

        if (ingredientsList.isEmpty()) {
            Logger.err("Empty ingredients list for shapeless recipe in item, skipping crafting")
            return null
        }

        val ingredients = ingredientsList.mapNotNull { ingredientMap ->
            parseIngredientEntry(ingredientMap)
        }

        return if (ingredients.isNotEmpty()) {
            ItemDefinition.CraftingRecipe.ShapelessCraftingRecipe(resultAmount, ingredients)
        } else {
            null
        }
    }


    private fun parseRecipeKeys(keySection: ConfigurationSection?): Map<Char, ItemDefinition.CraftingRecipe.IngredientEntry> {
        keySection ?: return emptyMap()

        return keySection.getKeys(false).mapNotNull { charKey ->
            if (charKey.length != 1) {
                Logger.err("Invalid key character '$charKey' for item, skipping this key entry")
                return@mapNotNull null
            }

            val keyEntrySection = keySection.getConfigurationSection(charKey) ?: return@mapNotNull null
            val ingredient = parseIngredientFromSection(keyEntrySection, charKey)
            ingredient?.let { charKey[0] to it }
        }.toMap()
    }

    private fun parseIngredientFromSection(
        section: ConfigurationSection,
        charKey: String
    ): ItemDefinition.CraftingRecipe.IngredientEntry? {
        val itemId = section.getString("item")
        val tag = section.getString("tag")
        val count = section.getInt("count", 1)

        return when {
            tag != null -> ItemDefinition.CraftingRecipe.IngredientEntry(null, tag, count)
            itemId != null -> ItemDefinition.CraftingRecipe.IngredientEntry(itemId, null, count)
            else -> {
                Logger.err("Missing 'item' or 'tag' in key entry for character '$charKey' in item")
                null
            }
        }
    }

    private fun parseIngredientEntry(ingredientMap: Map<out Any?, Any?>?): ItemDefinition.CraftingRecipe.IngredientEntry? {
        val itemId = ingredientMap?.get("item") as? String
        val tag = ingredientMap?.get("tag") as? String
        val count = (ingredientMap?.get("count") as? Number)?.toInt() ?: 1

        return when {
            tag != null -> ItemDefinition.CraftingRecipe.IngredientEntry(null, tag, count)
            itemId != null -> ItemDefinition.CraftingRecipe.IngredientEntry(itemId, null, count)
            else -> {
                Logger.err("Missing 'item' or 'tag' in ingredient for item")
                null
            }
        }
    }

}

