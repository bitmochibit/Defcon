package com.mochibit.defcon.save.strategy

import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import javassist.bytecode.SignatureAttribute.ClassType

sealed interface SaveStrategy<T : SaveSchema> {
    fun init(saveDataInfo: SaveDataInfo): SaveStrategy<T>
    fun save(schema: T)
    fun load(schema: T): T
}