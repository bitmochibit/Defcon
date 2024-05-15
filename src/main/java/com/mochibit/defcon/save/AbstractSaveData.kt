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

abstract class AbstractSaveData<T : SaveSchema> {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java) ?: throw IllegalStateException("SaveDataInfo annotation not found")

    lateinit var saveData: T
    private var saveStrategy: SaveStrategy<T> = JsonSaver<T>().init(saveDataInfo)

    fun save() {
        if (!::saveData.isInitialized) {
            throw IllegalStateException("saveData has not been initialized")
        }
        saveStrategy.save(saveData)
    }

    fun load(): AbstractSaveData<T> {
        if (!::saveData.isInitialized) {
            throw IllegalStateException("saveData has not been initialized")
        }
        saveData = saveStrategy.load(saveData)
        return this
    }


    fun setSaveStrategy(saveStrategy: SaveStrategy<T>): AbstractSaveData<T> {
        this.saveStrategy = saveStrategy
        saveStrategy.init(saveDataInfo)
        return this
    }


}