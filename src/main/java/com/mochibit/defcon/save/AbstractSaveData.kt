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

abstract class AbstractSaveData <T:SaveSchema> {
    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java)

    lateinit var saveData: T
    private var saveStrategy: SaveStrategy = JsonSaver().init(saveDataInfo)

    fun save() {
        saveStrategy.save(saveData)
    }

    open fun load(): AbstractSaveData<T> {
        val loadedData = saveStrategy.load(saveData)
        // Check if loadedData type is T
        if (loadedData::class !== saveData::class)
            throw IllegalArgumentException("Loaded data is not of type ${saveData::class.simpleName}")

        @Suppress("UNCHECKED_CAST")
        saveData = loadedData as T
        return this
    }

    fun setSaveStrategy(saveStrategy: SaveStrategy): AbstractSaveData<T> {
        this.saveStrategy = saveStrategy
        saveStrategy.init(saveDataInfo)
        return this
    }
}