package com.mochibit.defcon.save.strategy

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
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
    var filePage : Int? = null
    private val filePageString : String
        get() = if (filePage == null) "" else "-$filePage";

    val fileExtension = ".json"
    var completePath: Path
        get() = Paths.get(path.toString(), file.toString() + fileNameSuffix + filePageString + fileExtension)
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

    fun pageExists(): Boolean {
        return Files.exists(completePath)
    }

    fun nextPage() {
        if (this.filePage == null) return;
        this.filePage = this.filePage!! + 1;
    }

    fun setPage(page: Int) {
        this.filePage = page;
    }

    fun previousPage() {
        if (this.filePage!! <= 0) return;
        this.filePage = this.filePage!! - 1;
    }

    override fun init(saveDataInfo: SaveDataInfo, paginate: Boolean): SaveStrategy<T> {
        path = Paths.get(Defcon.instance.dataFolder.absolutePath, saveDataInfo.filePath)
        file = Paths.get(saveDataInfo.fileName)
        if (paginate) filePage = 0;

        return this
    }


}