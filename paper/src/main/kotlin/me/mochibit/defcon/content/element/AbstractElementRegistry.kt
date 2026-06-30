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

package me.mochibit.defcon.content.element

import me.mochibit.defcon.utils.Logger

/**
 * Abstract registry for immutable element templates.
 *
 * Each element definition has exactly ONE template instance stored in this registry.
 * Templates are read-only and should never be modified after registration.
 *
 * For items: Use `PluginItem.itemStack` to get a new ItemStack instance.
 * For blocks/structures: Use the template directly as they are stateless.
 *
 * @param P The properties type
 * @param E The element type
 * @param D The definition type
 */
abstract class AbstractElementRegistry<P : ElementProperties, E : Element<P, *>, D : ElementDefinition<P, E>>(
    val factory: AbstractElementFactory<P, E, D>,
) {
    private val elements = mutableMapOf<String, E>()
    private var cachedDefinitions: List<D>? = null

    open suspend fun registerAll() {
        Logger.info("Registering elements...")

        cachedDefinitions = null
        elements.clear()

        val definitions = getDefinitions()
        if (definitions.isEmpty()) {
            Logger.warn("No definitions found in the configuration, skipping registration")
            return
        }

        definitions.forEach { definition ->
            if (definition.id in elements) {
                Logger.warn("Element '${definition.id}' already exists.")
                return@forEach
            }

            val pluginElement = factory.create(definition)
            elements[definition.id] = pluginElement
            Logger.info("Registered element '${definition.id}'.")
        }
    }

    protected suspend fun getDefinitions(): List<D> = cachedDefinitions ?: retrieveDefinitions().also { cachedDefinitions = it }

    protected abstract suspend fun retrieveDefinitions(): List<D>

    /**
     * Get the immutable template element by ID. Returns null if not found.
     * WARNING: This is a read-only singleton. Do NOT modify it.
     * For ItemStacks, use `template.itemStack` to get a new instance.
     */
    operator fun get(id: String): E? = elements[id]

    /**
     * Get all registered element IDs
     */
    val registeredIds: Set<String> get() = elements.keys

    /**
     * Get all element templates
     */
    val all: Collection<E> get() = elements.values

    /**
     * Check if an element with the given ID is registered
     */
    operator fun contains(id: String): Boolean = id in elements
}
