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

import me.mochibit.defcon.config.PluginConfiguration
import me.mochibit.defcon.utils.Logger

abstract class AbstractElementRegistry<P : ElementProperties, E: Element, D: ElementDefinition<P, E>>(
    val factory: AbstractElementFactory<P, E, D>,
) {
    private val elements = mutableMapOf<String, E>()

    protected val cachedDefinitions: MutableList<D> = mutableListOf()

    open suspend fun registerAll() {
        Logger.info("Registering elements...")

        cachedDefinitions.clear()
        elements.clear()

        val definitions = getDefinitions()
        if (definitions.isEmpty()) {
            Logger.warn("No definitions found in the configuration, skipping registration")
            return
        }

        definitions.forEach { definition ->
            if (elements.containsKey(definition.id)) {
                Logger.warn("Element '${definition.id}' already exists.")
                return@forEach
            }

            val pluginElement = factory.create(definition)
            elements[definition.id] = pluginElement
            Logger.info("Registered element '${definition.id}'.")
        }
    }

    protected suspend fun getDefinitions(): List<D> {
        if (cachedDefinitions.isNotEmpty()) return cachedDefinitions
        return retrieveDefinitions().also {
            cachedDefinitions.addAll(it)
        }
    }

    protected abstract suspend fun retrieveDefinitions(): List<D>

    @Suppress("UNCHECKED_CAST")
    fun get(id: String): E? = elements[id]?.copied() as? E

    fun getAllTemplates(): Collection<E> = elements.values

    fun getTemplate(id: String): E? = elements[id]
}