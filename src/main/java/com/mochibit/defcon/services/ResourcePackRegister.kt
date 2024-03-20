package com.mochibit.defcon.services

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.gui.customfonts.AbstractCustomFont
import org.apache.commons.io.FileUtils
import org.bukkit.Bukkit
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.reflections.Reflections
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.*
import java.util.*
import java.util.jar.JarFile


class ResourcePackRegister private constructor() : PackRegister {
    companion object {
        val get = ResourcePackRegister()
    }

    private var packageName: String = Defcon.instance.javaClass.getPackage().name

    private var tempRootPath: Path = Paths.get("./defconResourcePack")

    // Register the resourcepack (sounds, textures, fonts..)
    override fun registerPack() {
        // TODO: Refactoring and code cleanup

        val serverVersion = Bukkit.getBukkitVersion().split("-")[0]
        info("Creating datapack for Defcon for minecraft version $serverVersion")

        // Create a folder named "defcon", then a subfolder "data", then a subfolder "defcon", then a subfolder "worldgen", then a subfolder "biome"
        // Create the data folder
        // Check if the folder exists, if yes, delete it
        if (Files.exists(tempRootPath)) {
            // Delete folder even if it's not empty
            Files.walk(tempRootPath)
                .sorted(Comparator.reverseOrder())
                .map { obj: Any? -> obj as java.nio.file.Path }
                .forEach { obj: java.nio.file.Path ->
                    try {
                        Files.delete(obj)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
        Files.createDirectories(tempRootPath)


        // Create .mcmeta file for the datapack
        val mcmeta = JSONObject()
        val pack = JSONObject()
        pack["pack_format"] = ResourcePackFormatVersion.valueOf("V${serverVersion.replace(".", "_")}").getVersion()
        pack["description"] = "Defcon auto-generated resource pack"
        mcmeta["pack"] = pack
        // Write the .mcmeta file to the rootPath
        Files.write(Paths.get("$tempRootPath/pack.mcmeta"), mcmeta.toJSONString().toByteArray())

        // Create the assets folder
        val localAssetsPath = Paths.get("$tempRootPath/assets")
        Files.createDirectories(localAssetsPath)


        // Inside the "assets" folder, inside the resource folder, copy everything except the "defcon" folder to the "assets" folder in the tempRootPath

        val jarFile = File(Defcon::class.java.protectionDomain.codeSource.location.path)
        val jar = JarFile(jarFile)
        var entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("assets/") && !entry.name.startsWith("assets/defcon")) {
                val path = Paths.get(tempRootPath.toString(), entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(path)
                } else {
                    Files.copy(jar.getInputStream(entry), path, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }


        // Create the "minecraft" folder
        val minecraftPath = Paths.get("$tempRootPath/assets/minecraft")
        Files.createDirectories(minecraftPath)

        // Create the "font" folder
        val fontPath = Paths.get("$minecraftPath/font")
        Files.createDirectories(fontPath)
        // Copy the default.json file from the resources folder to the font folder
        entries = jar.entries()
        while(entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.name.startsWith("assets/"))
                continue

            info(entry.name)
            if (entry.name.equals("assets/defcon/fonts/default.json")) {
                Files.copy(jar.getInputStream(entry), fontPath.resolve("default.json"), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        val jsonReader = Files.newBufferedReader(fontPath.resolve("default.json"))
        val jsonParser = JSONParser()
        val defaultJson = jsonParser.parse(jsonReader) as JSONObject
        val providers = defaultJson["providers"] as JSONArray


        // Get all the subtypes of AbstractCustomFont inside the "com.mochibit.defcon.gui.customfonts.definitions" package
        for (font in Reflections("$packageName.gui.customfonts.definitions").getSubTypesOf(AbstractCustomFont::class.java)) {
            val fontInstance = font.getDeclaredConstructor().newInstance()
            val fontData = fontInstance.fontData

            val newFont = JSONObject()
            newFont["file"] = fontData.file
            newFont["type"] = fontData.type
            newFont["ascent"] = fontData.ascent
            newFont["height"] = fontData.height
            newFont["chars"] = JSONArray().addAll(fontData.chars)
            newFont["advances"] = fontData.advances

            providers.add(newFont)
        }
        // Write the new default.json file
        Files.write(fontPath.resolve("default.json"), defaultJson.toJSONString().toByteArray())


        // Inside the resources folder, inside the defcon folder, there's a "sounds" folder. Copy every folder inside the "sounds" folder to the "sounds" folder in the minecraft folder
        val soundsPath = Paths.get("src/main/resources/assets/defcon/sounds")
        val targetSoundsPath = Paths.get("$minecraftPath/sounds")
        Files.walk(soundsPath)
            .filter { path -> Files.isDirectory(path) }
            .forEach { path ->
                val relativePath = soundsPath.relativize(path)
                val targetPath = targetSoundsPath.resolve(relativePath)
                Files.createDirectories(targetPath)
                Files.walk(path)
                    .forEach { sourcePath ->
                        val relativeSourcePath = path.relativize(sourcePath)
                        val targetSourcePath = targetPath.resolve(relativeSourcePath)
                        Files.copy(sourcePath, targetSourcePath)
                    }
            }

        // Inside the resources folder, inside the defcon folder, there's a "textures" folder. Copy every folder inside the "textures" folder to the "textures" folder in the minecraft folder
        val texturesPath = Paths.get("src/main/resources/assets/defcon/textures")
        val targetTexturesPath = Paths.get("$minecraftPath/textures")
        Files.walk(texturesPath)
            .filter { path -> Files.isDirectory(path) }
            .forEach { path ->
                val relativePath = texturesPath.relativize(path)
                val targetPath = targetTexturesPath.resolve(relativePath)
                Files.createDirectories(targetPath)
                Files.walk(path)
                    .forEach { sourcePath ->
                        val relativeSourcePath = path.relativize(sourcePath)
                        val targetSourcePath = targetPath.resolve(relativeSourcePath)
                        Files.copy(sourcePath, targetSourcePath)
                    }
            }


    }

}

enum class ResourcePackFormatVersion(private val version: Int) {
    V1_13(4), V1_14(4),
    V1_15(5), V1_16_1(5),
    V1_16_2(6), V1_16_5(6),
    V1_17(7), V1_17_1(7),
    V1_18(8), V1_18_2(8),
    V1_19(9), V1_19_1(9), V1_19_2(9),
    V1_19_3(12),
    V1_19_4(13);

    fun getVersion(): Int {
        return version
    }
}