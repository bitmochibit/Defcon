package com.mochibit.defcon.registers

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.biomes.CustomBiome
import com.mochibit.defcon.biomes.data.BiomeInfo
import org.bukkit.Bukkit
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.reflections.Reflections
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class DatapackRegister private constructor() : PackRegister {
    companion object {
        val get = DatapackRegister()
    }

    private var packageName: String = Defcon.instance.javaClass.getPackage().name
    var datapackFolder = Paths.get("world", "datapacks")
    var defconDatapackFolder = Paths.get(datapackFolder.toString(), "defcon")

    private var tempRootPath: Path = Paths.get("./defconTempDatapack")
    private var tempBiomesPath = Paths.get("$tempRootPath/data/defcon/worldgen/biome")

    // Register the datapack (biomes, worldgen, advancements, structures..)
    override fun registerPack() {
        // TODO: Refactoring and code cleanup

        val serverVersion = Bukkit.getBukkitVersion().split("-")[0]
        info("Creating datapack for Defcon for minecraft version $serverVersion")

        if (!Files.exists(datapackFolder))
            throw Exception("Datapack folder not found $datapackFolder")

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
        pack["description"] = "Defcon datapack"
        mcmeta["pack"] = pack
        // Write the .mcmeta file to the rootPath
        Files.write(Paths.get("$tempRootPath/pack.mcmeta"), mcmeta.toJSONString().toByteArray())


        registerBiomes()

        // Move the defcon datapack to the "world" folder

        // Hash the temp datapack folder and check if it's the same as the one in the world folder
        // If it's the same, don't move it
        // If it's different, move it and restart the server

        val tempHash = Files.walk(tempRootPath)
            .filter { path -> Files.isRegularFile(path) }
            .map { path -> path.toFile().readText() }
            .reduce { acc, s -> acc + s }
            .hashCode()

        val defconHash = if (Files.exists(defconDatapackFolder)) {
            Files.walk(defconDatapackFolder)
                .filter { path -> Files.isRegularFile(path) }
                .map { path -> path.toFile().readText() }
                .reduce { acc, s -> acc + s }
                .hashCode()
        } else {
            0
        }

        info("Temp hash: $tempHash | Defcon hash: $defconHash")

        if (tempHash == defconHash)
            return

        info("Moving datapack to $defconDatapackFolder")
        if (Files.exists(defconDatapackFolder)) {
            // Delete folder even if it's not empty
            Files.walk(defconDatapackFolder)
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
        Files.move(tempRootPath, defconDatapackFolder)

        Bukkit.shutdown()

    }

    private fun registerBiomes() {
        Files.createDirectories(tempBiomesPath)

        val parentJson = JSONObject()
        // Get all the biomes class from "biomes" with annotation BiomeInfo
        for (biomes in Reflections("$packageName.biomes.definitions").getTypesAnnotatedWith(
            BiomeInfo::class.java,
            true
        )) {
            val biome = biomes.getConstructor().newInstance()
            if (biome !is CustomBiome)
                continue
            val default = biome.build()
            parentJson["temperature"] = default.temperature
            parentJson["downfall"] = default.downfall
            parentJson["precipitation"] = default.precipitation.name.lowercase(Locale.getDefault())
            parentJson["temperature_modifier"] = default.temperatureModifier.name.lowercase(Locale.getDefault())
            parentJson["has_precipitation"] = default.hasPrecipitation

            val effects = JSONObject()
            effects["sky_color"] = default.effects.skyColor
            effects["fog_color"] = default.effects.fogColor
            effects["water_color"] = default.effects.waterColor
            effects["water_fog_color"] = default.effects.waterFogColor

            if (default.effects.moodSound != null) {
                val moodSound = JSONObject()
                moodSound["sound"] = default.effects.moodSound!!.sound
                moodSound["tick_delay"] = default.effects.moodSound!!.tickDelay
                moodSound["block_search_extent"] = default.effects.moodSound!!.blockSearchExtent
                moodSound["offset"] = default.effects.moodSound!!.offset
                effects["mood_sound"] = moodSound
            }

            if (default.effects.additionalSound != null) {
                val additionalSound = JSONObject()
                additionalSound["sound"] = default.effects.additionalSound!!.sound
                additionalSound["tick_chance"] = default.effects.additionalSound!!.tickChance
                effects["additional_sound"] = additionalSound
            }

            if (default.effects.particle != null) {
                val particle = JSONObject()
                particle["probability"] = default.effects.particle!!.probability

                val options = JSONObject()
                options["type"] =
                    "minecraft:${default.effects.particle!!.particle.name.lowercase(Locale.getDefault())}"
                default.effects.particle!!.color?.let { options["color"] = it }
                default.effects.particle!!.size?.let { options["size"] = it }
                default.effects.particle!!.material?.let {
                    options["material"] = "minecraft:${it.name.lowercase(Locale.getDefault())}"
                }
                default.effects.particle!!.fromColor?.let { options["from_color"] = it }
                default.effects.particle!!.toColor?.let { options["to_color"] = it }
                particle["options"] = options

                effects["particle"] = particle
            }

            if (default.effects.music != null && default.effects.music!!.sound != null) {
                val music = JSONObject()
                music["sound"] = default.effects.music!!.sound
                music["min_delay"] = default.effects.music!!.minDelay
                music["max_delay"] = default.effects.music!!.maxDelay
                music["replace_current_music"] = default.effects.music!!.replaceCurrentMusic
                effects["music"] = music
            }

            default.effects.ambientSound?.let { effects["ambient_sound"] = it }

            parentJson["effects"] = effects

            val spawners = JSONObject()
            // For now, empty object
            parentJson["spawners"] = spawners

            val spawnCosts = JSONObject()
            // For now, empty object
            parentJson["spawn_costs"] = spawnCosts

            val carvers = JSONObject()
            // For now, empty object
            parentJson["carvers"] = carvers

            val features = JSONArray()
            // For now, empty array
            parentJson["features"] = features

            // Write the json file
            Files.write(
                Paths.get("$tempBiomesPath/${biome.biomeKey.value()}.json"),
                parentJson.toJSONString().toByteArray()
            )
        }

    }
}

enum class DatapackFormatVersion(private val version: Int) {
    V1_13(4), V1_14(4),
    V1_15(5), V1_16_1(5),
    V1_16_2(6), V1_16_5(6),
    V1_17(7), V1_17_1(7),
    V1_18(8), V1_18_1(8),
    V1_18_2(9),
    V1_19(10), V1_19_1(10), V1_19_2(10), V1_19_3(10),
    V1_19_4(12);

    fun getVersion(): Int {
        return version
    }
}