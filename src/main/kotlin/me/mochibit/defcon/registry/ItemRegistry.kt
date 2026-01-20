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

package me.mochibit.defcon.registry

import me.mochibit.defcon.config.ItemsConfiguration
import me.mochibit.defcon.config.ItemsConfiguration.ItemDefinition.CraftingRecipe
import me.mochibit.defcon.content.element.AbstractElementRegistry
import me.mochibit.defcon.content.items.PluginItem
import me.mochibit.defcon.content.items.PluginItemFactory
import me.mochibit.defcon.content.items.PluginItemProperties
import me.mochibit.defcon.utils.Logger.info

/**
 * Registry for all custom plugin items.
 *
 * This registry handles:
 * - Registration of custom items from configuration
 * - Registration of crafting recipes for items
 * - Template storage for item instances
 */
object ItemRegistry : AbstractElementRegistry<PluginItemProperties, PluginItem<*>, ItemsConfiguration.ItemDefinition>(
    PluginItemFactory,
) {
    override suspend fun registerAll() {
        super.registerAll()
        registerRecipes()
    }

    private suspend fun registerRecipes() {
        info("Registering recipes for items")

        getDefinitions()
            .mapNotNull { definition ->
                definition.craftingRecipe?.let { recipe -> definition to recipe }
            }.forEach { (definition, recipe) ->
                registerRecipe(definition, recipe)
            }
    }

    private fun registerRecipe(
        definition: ItemsConfiguration.ItemDefinition,
        recipe: CraftingRecipe,
    ) {
        val itemTemplate =
            this[definition.id] ?: run {
                info("Item template '${definition.id}' not found, skipping recipe registration")
                return
            }

        // itemStack property creates a new instance each time
        val resultItemStack = itemTemplate.itemStack

        when (recipe) {
            is CraftingRecipe.ShapedCraftingRecipe -> {
                RecipeRegistrar.registerShapedRecipe(definition.id, recipe, resultItemStack)
            }

            is CraftingRecipe.ShapelessCraftingRecipe -> {
                RecipeRegistrar.registerShapelessRecipe(definition.id, recipe, resultItemStack)
            }
        }
    }

    override suspend fun retrieveDefinitions(): List<ItemsConfiguration.ItemDefinition> = ItemsConfiguration.getItemDefinitions()

    /**
     * Alias for get() - returns the immutable item template
     */
    fun getItem(id: String): PluginItem<*>? = this[id]
}
