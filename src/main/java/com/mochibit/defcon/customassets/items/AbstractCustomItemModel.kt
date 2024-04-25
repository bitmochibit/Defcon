package com.mochibit.defcon.customassets.items

abstract class AbstractCustomItemModel (
    val modelData: ModelData
)
{
    val modelInfo: ModelInfo = this::class.java.getAnnotation(ModelInfo::class.java)
}