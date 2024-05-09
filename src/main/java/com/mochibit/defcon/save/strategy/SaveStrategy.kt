package com.mochibit.defcon.save.strategy

import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import javassist.bytecode.SignatureAttribute.ClassType

sealed interface SaveStrategy {
    fun init(saveDataInfo: SaveDataInfo): SaveStrategy
    fun save(schema: SaveSchema)
    fun load(schema: SaveSchema): SaveSchema

}