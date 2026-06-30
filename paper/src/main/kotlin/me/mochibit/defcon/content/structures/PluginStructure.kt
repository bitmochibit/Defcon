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

package me.mochibit.defcon.content.structures

import me.mochibit.defcon.content.element.Element
import me.mochibit.defcon.content.element.ElementBehaviourPropParser
import me.mochibit.defcon.content.element.ElementBehaviourProperties

/**
 * Abstract base class for all plugin structures.
 *
 * @param B The behaviour properties type for this structure (use Nothing? for structures without special behavior)
 */
abstract class PluginStructure<out B : ElementBehaviourProperties?>(
    open override val properties: PluginStructureProperties,
    open override val unparsedBehaviourData: Map<String, Any>,
    final override val behaviourPropParser: ElementBehaviourPropParser? = null,
) : Element<PluginStructureProperties, B> {
    /**
     * Lazily computed behavior properties. Override in subclasses that need typed access.
     */
    @Suppress("UNCHECKED_CAST")
    override val behaviourProperties: B
        get() = behaviourPropParser?.parse(unparsedBehaviourData) as B
}
