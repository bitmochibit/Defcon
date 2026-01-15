/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025-2026 mochibit.
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

package me.mochibit.defcon.registry

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.config.ItemsConfiguration
import me.mochibit.defcon.utils.Logger
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe

/**
 * Helper class for registering Minecraft crafting recipes
 */
internal object RecipeRegistrar {
    fun registerShapedRecipe(
        itemId: String,
        recipe: ItemsConfiguration.ItemDefinition.CraftingRecipe.ShapedCraftingRecipe,
        resultItemStack: ItemStack,
    ): Boolean {
        // Validate pattern
        if (recipe.pattern.size > 3 || recipe.pattern.any { it.length > 3 }) {
            Logger.err("Invalid pattern size for item $itemId, must be 3x3 or smaller")
            return false
        }

        val namespacedKey = NamespacedKey(Defcon, "item_${itemId}_shaped")
        val shapedRecipe = ShapedRecipe(namespacedKey, resultItemStack.apply { amount = recipe.resultAmount })

        shapedRecipe.shape(*recipe.pattern.toTypedArray())

        // Register ingredients
        recipe.keys.forEach { (char, ingredientEntry) ->
            val choice = ingredientEntry.toRecipeChoice(itemId) ?: return@forEach
            shapedRecipe.setIngredient(char, choice)
        }

        Bukkit.addRecipe(shapedRecipe)
        Logger.info("Registered shaped recipe for item $itemId")
        return true
    }

    fun registerShapelessRecipe(
        itemId: String,
        recipe: ItemsConfiguration.ItemDefinition.CraftingRecipe.ShapelessCraftingRecipe,
        resultItemStack: ItemStack,
    ): Boolean {
        val namespacedKey = NamespacedKey(Defcon, "item_${itemId}_shapeless")
        val shapelessRecipe = ShapelessRecipe(namespacedKey, resultItemStack.apply { amount = recipe.resultAmount })

        recipe.ingredients.forEach { ingredientEntry ->
            val choice = ingredientEntry.toRecipeChoice(itemId) ?: return@forEach
            repeat(ingredientEntry.count) { shapelessRecipe.addIngredient(choice) }
        }

        Bukkit.addRecipe(shapelessRecipe)
        Logger.info("Registered shapeless recipe for item $itemId")
        return true
    }

    private fun ItemsConfiguration.ItemDefinition.CraftingRecipe.IngredientEntry.toRecipeChoice(itemId: String): RecipeChoice? {
        return when {
            itemNamespaced != null -> {
                val ingredientItem = NamespaceResolver.getItemStack(itemNamespaced, count)
                if (ingredientItem == null) {
                    Logger.err("Ingredient '$itemNamespaced' not found for recipe of item $itemId")
                    return null
                }
                RecipeChoice.ExactChoice(ingredientItem)
            }

            tag != null -> {
                val materialTag = NamespaceResolver.getMaterialTag(tag)
                if (materialTag == null) {
                    Logger.err("Invalid material tag '$tag' for recipe of item $itemId")
                    return null
                }
                RecipeChoice.MaterialChoice(materialTag)
            }

            else -> {
                Logger.err("Ingredient entry for recipe of item $itemId must have either 'itemNamespaced' or 'tag'")
                null
            }
        }
    }
}

/**
 * Resolves namespaced identifiers to Minecraft resources
 */
internal object NamespaceResolver {
    fun getItemStack(
        namespace: String,
        amount: Int = 1,
    ): ItemStack? {
        val parts = namespace.split(":")
        if (parts.size != 2) {
            Logger.err("Invalid namespace format: $namespace. Expected format is 'namespace:key'")
            return null
        }

        val (prefix, key) = parts
        return when (prefix) {
            "minecraft" -> {
                createMinecraftItemStack(key, amount)
            }

            "defcon" -> {
                createDefconItemStack(key, amount)
            }

            else -> {
                Logger.err("Unknown namespace: $prefix")
                null
            }
        }
    }

    fun getMaterialTag(tagString: String): Tag<Material>? {
        val key = NamespacedKey.fromString(tagString) ?: return null
        return listOf("blocks", "items", "fluids")
            .firstNotNullOfOrNull { registry -> Bukkit.getTag(registry, key, Material::class.java) }
    }

    private fun createMinecraftItemStack(
        materialName: String,
        amount: Int,
    ): ItemStack? {
        val material = Material.getMaterial(materialName.uppercase())
        return material?.let { ItemStack(it, amount) }
            ?: run {
                Logger.err("Material $materialName not found in minecraft namespace")
                null
            }
    }

    private fun createDefconItemStack(
        itemId: String,
        amount: Int,
    ): ItemStack? {
        val customItem = ItemRegistry[itemId] ?: return null
        return customItem.itemStack.apply { this.amount = amount }
    }
}
