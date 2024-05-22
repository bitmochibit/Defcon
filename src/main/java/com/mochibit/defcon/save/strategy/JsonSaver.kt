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
import java.util.function.Supplier
import kotlin.reflect.KClass


class JsonSaver <T: SaveSchema> (saveDataInfo: SaveDataInfo, private val schemaClass: KClass<out T>) : SaveStrategy<T> {
    private var path: Path = Paths.get(Defcon.instance.dataFolder.absolutePath, saveDataInfo.filePath)
    private var file = Paths.get(saveDataInfo.fileName)
    var fileNameSuffix : Supplier<String> = Supplier { "" }
    private val fileExtension = ".json"

    private var completePath: (Int?) -> Path = { page -> Paths.get(path.toString(), file.toString() + fileNameSuffix.get() + pageToString(page ?: 0) + fileExtension) }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    override fun save(schema: T, page: Int?) {
        val json = gson.toJson(schema)
        // Create folder if it doesn't exist
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }

        Files.write(completePath(page), json.toByteArray())
    }

    override fun load(page: Int?): T? {
        if (!this.completePath(page).toFile().exists()) {
            return null;
        }
        val jsonReader = this.completePath(page).toFile().bufferedReader()
        val json = jsonReader.use { it.readText() }
        return gson.fromJson(json, schemaClass.java)
    }

    override fun pageExists(page: Int): Boolean {
        return Files.exists(completePath(page))
    }
    fun pageToString(page: Int?): String {
        return if (page!= null) "-$page" else ""
    }
}