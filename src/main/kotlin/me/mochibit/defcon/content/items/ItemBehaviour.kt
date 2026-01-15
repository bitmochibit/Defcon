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

package me.mochibit.defcon.content.items

import me.mochibit.defcon.content.blocks.fissionCore.FissionCoreBlock
import me.mochibit.defcon.content.blocks.fissionCore.FissionCoreBlockItem
import me.mochibit.defcon.content.blocks.fusionCore.FusionCoreBlock
import me.mochibit.defcon.content.blocks.fusionCore.FusionCoreBlockItem
import me.mochibit.defcon.content.blocks.warheadInterface.WarheadInterfaceBlockItem
import me.mochibit.defcon.content.element.ElementBehaviour
import me.mochibit.defcon.content.items.gasMask.GasMaskItem
import me.mochibit.defcon.content.items.radiationHealer.RadiationHealerItem
import me.mochibit.defcon.content.items.radiationMeasurer.RadiationMeasurerItem
import me.mochibit.defcon.content.items.structureAssembler.StructureAssemblerItem

enum class ItemBehaviour : ElementBehaviour<PluginItemProperties, PluginItem<*>> {
    GAS_MASK {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = GasMaskItem(properties, behaviourData)
    },
    RADIATION_MEASURER {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = RadiationMeasurerItem(properties, behaviourData)
    },
    RADIATION_HEALER {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = RadiationHealerItem(properties, behaviourData)
    },
    STRUCTURE_ASSEMBLER {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = StructureAssemblerItem(properties, behaviourData)
    },

    // BLOCK ITEMS
    FISSION_CORE {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = FissionCoreBlockItem(properties, behaviourData)
    },
    FUSION_CORE {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = FusionCoreBlockItem(properties, behaviourData)
    },
    WARHEAD_INTERFACE {
        override fun create(
            properties: PluginItemProperties,
            behaviourData: Map<String, Any>,
        ): PluginItem<*> = WarheadInterfaceBlockItem(properties, behaviourData)
    },
}
