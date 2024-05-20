package com.mochibit.defcon.save.strategy

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass


class JsonSaver <T: SaveSchema> (private val schemaClass: KClass<out T>) : SaveStrategy<T> {
    lateinit var path: Path
    lateinit var file: Path
    var fileNameSuffix : String = ""
    val fileExtension = ".json"
    var completePath: Path
        get() = Paths.get(path.toString(), file.toString() + fileNameSuffix + fileExtension)
        set(value) {
            file = value
        }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    override fun save(schema: T) {
        val json = getJson(schema)
        // Create folder if it doesn't exist
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }

        Files.write(completePath, json.toByteArray())
    }

    override fun load(): T? {
        if (!this.completePath.toFile().exists()) {
            return null;
        }
        val jsonReader = this.completePath.toFile().bufferedReader()
        val json = jsonReader.use { it.readText() }
        return gson.fromJson(json, schemaClass.java)
    }

    private fun getJson(schema: SaveSchema): String {
        return gson.toJson(schema)
    }


    override fun init(saveDataInfo: SaveDataInfo): SaveStrategy<T> {
        path = Paths.get(Defcon.instance.dataFolder.absolutePath, saveDataInfo.filePath)
        file = Paths.get(saveDataInfo.fileName)
        return this
    }


}