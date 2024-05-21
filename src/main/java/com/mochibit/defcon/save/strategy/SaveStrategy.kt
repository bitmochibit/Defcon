package com.mochibit.defcon.save.strategy

import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import javassist.bytecode.SignatureAttribute.ClassType

sealed interface SaveStrategy<T : SaveSchema> {
    fun init(saveDataInfo: SaveDataInfo, paginate: Boolean): SaveStrategy<T>
    fun save(schema: T)
    fun load(): T?
}