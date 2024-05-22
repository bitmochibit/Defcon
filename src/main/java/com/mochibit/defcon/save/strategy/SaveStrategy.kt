package com.mochibit.defcon.save.strategy

import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import javassist.bytecode.SignatureAttribute.ClassType

sealed interface SaveStrategy<T : SaveSchema> {
    fun save(schema: T, page: Int? = null)
    fun load(page: Int? = null): T?
    fun pageExists(page: Int): Boolean
}