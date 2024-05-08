package com.mochibit.defcon.save

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.save.schemas.SaveSchema
import com.mochibit.defcon.save.savedata.SaveDataInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractSaveData <T:SaveSchema> {
    // TODO: Implement a system to split the save data into multiple files if it gets too large and index them

    private val saveDataInfo = this.javaClass.getAnnotation(SaveDataInfo::class.java)

    protected val path: Path = Paths.get(Defcon.instance.dataFolder.absolutePath, saveDataInfo.filePath)
    protected val file: Path = Paths.get(saveDataInfo.fileName + ".json")
    protected val completePath: Path = Paths.get(path.toString(), file.toString())

    lateinit var saveData: T

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    fun save() {
        val json = getJson()
        // Create folder if it doesn't exist
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }

        Files.write(completePath, json.toByteArray())
    }
    open fun getJson(): String {
        return gson.toJson(this.saveData)
    }
    open fun load(): AbstractSaveData<T> {
        if (!this.completePath.toFile().exists()) {
            return this
        }
        val jsonReader = this.completePath.toFile().bufferedReader()
        val json = jsonReader.use { it.readText() }
        this.saveData = gson.fromJson(json, this.saveData::class.java)
        return this;
    }
}