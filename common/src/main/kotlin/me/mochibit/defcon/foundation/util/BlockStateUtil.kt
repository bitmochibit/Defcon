package me.mochibit.defcon.foundation.util

import net.minecraft.world.level.block.state.BlockState

fun BlockState.copyPropertiesTo(target: BlockState) : BlockState {
    var result = target
    for (property in this.properties) {
        if (target.hasProperty(property)) {
            result = this.copySingleProperty(result, property)
        }
    }
    return result
}

fun <T : Comparable<T>> BlockState.copySingleProperty(
    target: BlockState,
    property: net.minecraft.world.level.block.state.properties.Property<T>,
): BlockState = target.setValue(property, this.getValue(property))