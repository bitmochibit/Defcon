package com.mochibit.defcon.save.strategy

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.save.savedata.SaveDataInfo
import com.mochibit.defcon.save.schemas.SaveSchema
import com.sun.jdi.ClassType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class JsonSaver() : SaveStrategy {
    // TODO: Implement a system to split the save data into multiple files if it gets too large and index them

    lateinit var path: Path
    lateinit var file: Path
    lateinit var completePath: Path

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    override fun save(schema: SaveSchema) {
        val json = getJson(schema)
        // Create folder if it doesn't exist
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }

        Files.write(completePath, json.toByteArray())
    }

    override fun load(schema: SaveSchema): SaveSchema {
        if (!this.completePath.toFile().exists()) {
            return schema
        }
        val jsonReader = this.completePath.toFile().bufferedReader()
        val json = jsonReader.use { it.readText() }
        return gson.fromJson(json, schema::class.java)
    }

    private fun getJson(schema: SaveSchema): String {
        return gson.toJson(schema)
    }

    override fun init(saveDataInfo: SaveDataInfo): SaveStrategy {
        path = Paths.get(Defcon.instance.dataFolder.absolutePath, saveDataInfo.filePath)
        file = Paths.get(saveDataInfo.fileName + ".json")
        completePath = Paths.get(path.toString(), file.toString())
        return this
    }
}