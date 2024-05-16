package com.mochibit.defcon.save

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.save.schemas.SaveSchema
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.strategy.JsonSaver
import com.mochibit.defcon.save.strategy.SaveStrategy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractSaveData<T : SaveSchema>(var saveData: T ) {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java) ?: throw IllegalStateException("SaveDataInfo annotation not found")
    private var saveStrategy = JsonSaver<T>().init(saveDataInfo)

    fun save() {
        saveStrategy.save(saveData)
    }

    fun load(): AbstractSaveData<T> {
        saveData = saveStrategy.load(saveData)
        return this
    }


    fun setSaveStrategy(saveStrategy: SaveStrategy<T>): AbstractSaveData<T> {
        this.saveStrategy = saveStrategy
        saveStrategy.init(saveDataInfo)
        return this
    }


}