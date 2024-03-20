package com.mochibit.defcon.services

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.customassets.customfonts.AbstractCustomFont
import com.mochibit.defcon.customassets.customsounds.AbstractCustomSound
import org.bukkit.Bukkit
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.reflections.Reflections
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.*
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


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
        copyFoldersFromResource("assets/", localAssetsPath, hashSetOf("assets/defcon"))


        // Create the "minecraft" folder
        val minecraftPath = Paths.get("$localAssetsPath/minecraft")
        Files.createDirectories(minecraftPath)

        // Create the "font" folder
        val fontPath = Paths.get("$minecraftPath/font")
        Files.createDirectories(fontPath)
        // Copy the default.json file from the resources folder to the font folder
        val jarFile = File(Defcon::class.java.protectionDomain.codeSource.location.path)
        val jar = JarFile(jarFile)
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.name.startsWith("assets/"))
                continue

            info(entry.name)
            if (entry.name.equals("assets/defcon/fonts/default.json")) {
                Files.copy(
                    jar.getInputStream(entry),
                    fontPath.resolve("default.json"),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }

        val jsonReader = Files.newBufferedReader(fontPath.resolve("default.json"))
        val jsonParser = JSONParser()
        val defaultJson = jsonParser.parse(jsonReader) as JSONObject
        val providers = defaultJson["providers"] as JSONArray


        // Get all the subtypes of AbstractCustomFont inside the "com.mochibit.defcon.gui.customfonts.definitions" package
        for (font in Reflections("$packageName.customassets.customfonts.definitions").getSubTypesOf(AbstractCustomFont::class.java)) {
            val fontInstance = font.getDeclaredConstructor().newInstance()
            val fontData = fontInstance.fontData

            val newFont = JSONObject()
            newFont["file"] = fontData.file
            newFont["type"] = fontData.type
            newFont["ascent"] = fontData.ascent
            newFont["height"] = fontData.height
            val chars = JSONArray()
            chars.addAll(fontData.chars.toArray())

            newFont["chars"] = chars
            newFont["advances"] = fontData.advances

            providers.add(newFont)
        }
        // Write the new default.json file
        Files.write(fontPath.resolve("default.json"), defaultJson.toJSONString().toByteArray())


        // Inside the resources folder, inside the defcon folder, there's a "sounds" folder. Copy every folder inside the "sounds" folder to the "sounds" folder in the minecraft folder
        val targetSoundsPath = Paths.get("$minecraftPath/sounds")
        copyFoldersFromResource("assets/defcon/sounds/", targetSoundsPath)

        // Create sounds.json file inside minecraft folder
        val soundsJson = JSONObject()
        for (sound in Reflections("$packageName.customassets.customsounds.definitions").getSubTypesOf(AbstractCustomSound::class.java)) {
            val soundInstance = sound.getDeclaredConstructor().newInstance()
            val soundInfo = soundInstance.soundInfo
            val soundData = soundInstance.soundData

            val soundObject = JSONObject()
            val soundsArray = JSONArray()
            soundsArray.addAll(soundData.sounds.toArray())
            soundObject["sounds"] = soundsArray

            if (soundInfo.directory != "") {
                soundsJson["${soundInfo.directory}.${soundInfo.name}"] = soundObject
            } else {
                soundsJson[soundInfo.name] = soundObject
            }
        }
        Files.write(minecraftPath.resolve("sounds.json"), soundsJson.toJSONString().toByteArray())

        // Inside the resources folder, inside the defcon folder, there's a "textures" folder. Copy every folder inside the "textures" folder to the "textures" folder in the minecraft folder

        val targetTexturesPath = Paths.get("$minecraftPath/textures")
        copyFoldersFromResource("assets/defcon/textures/", targetTexturesPath)

        // Zip the tempRootPath folder
        val zipPath = Paths.get("./DefconResourcePack.zip")
        if (Files.exists(zipPath)) {
            Files.delete(zipPath)
        }

        zipFolder(tempRootPath, zipPath, true)
    }

}

fun zipFolder(folderPath: Path, zipPath: Path, zipFolderPathContent: Boolean = false) {
    val fos = FileOutputStream(zipPath.toString())
    val zipOut = ZipOutputStream(fos)

    val fileToZip = folderPath.toFile()
    if (fileToZip.isDirectory) {
        if (zipFolderPathContent) {
            val children = fileToZip.listFiles()
            for (childFile in children) {
                zipFile(childFile, childFile.name, zipOut)
            }
        } else {
            zipFile(fileToZip, fileToZip.name, zipOut)
        }
    } else {
        zipFile(fileToZip, fileToZip.name, zipOut)
    }

    zipOut.close()
    fos.close()
}

@Throws(IOException::class)
private fun zipFile(fileToZip: File, fileName: String, zipOut: ZipOutputStream) {
    if (fileToZip.isHidden) {
        return
    }
    if (fileToZip.isDirectory) {
        if (fileName.endsWith("/")) {
            zipOut.putNextEntry(ZipEntry(fileName))
            zipOut.closeEntry()
        } else {
            zipOut.putNextEntry(ZipEntry("$fileName/"))
            zipOut.closeEntry()
        }
        val children = fileToZip.listFiles()
        for (childFile in children) {
            zipFile(childFile, fileName + "/" + childFile.name, zipOut)
        }
        return
    }
    val fis = FileInputStream(fileToZip)
    val zipEntry = ZipEntry(fileName)
    zipOut.putNextEntry(zipEntry)
    val bytes = ByteArray(1024)
    var length: Int
    while ((fis.read(bytes).also { length = it }) >= 0) {
        zipOut.write(bytes, 0, length)
    }
    fis.close()
}

fun copyFoldersFromResource(
    resourceFolderPath: String,
    targetPath: Path,
    excludedPaths: HashSet<String> = hashSetOf()
) {
    val jarFile = File(Defcon::class.java.protectionDomain.codeSource.location.path)
    val jar = JarFile(jarFile)
    val entries = jar.entries()
    entriesLoop@ while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        for (excludedPath in excludedPaths) {
            if (entry.name.startsWith(excludedPath)) continue@entriesLoop
        }

        // Virtually enter the "resourceFolderPath" and copy all the files/folders inside it to the "targetPath"
        // When copying the files/folders it should not keep the structure of the "resourceFolderPath" folder, but it should copy the files/folders directly to the "targetPath"
        if (entry.name.startsWith(resourceFolderPath)) {
            // The initial path is a string because inside the jar it's not a filesystem path
            val initialPath = entry.name.substring(resourceFolderPath.length)
            val target = targetPath.resolve(initialPath)
            if (entry.isDirectory) {
                Files.createDirectories(target)
            } else {
                Files.copy(jar.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING)
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