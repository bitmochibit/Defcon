/*
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.mochibit.defcon.registers

import me.mochibit.defcon.Defcon.Companion.Logger.info
import me.mochibit.defcon.biomes.BiomeRegister
import me.mochibit.defcon.biomes.CustomBiome
import me.mochibit.defcon.biomes.data.BiomeEffects
import me.mochibit.defcon.notification.Notification
import me.mochibit.defcon.notification.NotificationManager
import me.mochibit.defcon.notification.NotificationType
import me.mochibit.defcon.registers.packformat.FormatReader
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor.color
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Handles registration and generation of the datapack.
 */
object DatapackRegister : PackRegister() {
    override val tempPath: Path = Paths.get("./defcon_temp_datapack")

    private val tempBiomesPath: Path by lazy {
        val path = Paths.get("$tempPath/data/defcon/worldgen/biome")
        Files.createDirectories(path)
        path
    }

    private val messageForServerRestart = Component.text("The server must be restarted to use Defcon's datapack.")
        .color(color(0xFB6709))
        .decoration(TextDecoration.BOLD, true)
        .decoration(TextDecoration.ITALIC, false)


    private val defaultWorldName = Bukkit.getWorlds()[0].name
    private val datapackFolder: Path = Paths.get(defaultWorldName, "datapacks")

    override val destinationPath: Path? = Paths.get(datapackFolder.toString(), "defcon")

    /**
     * Writes the datapack content to the temp directory.
     */
    override fun write() {
        info("Registering Datapack")

        if (!Files.exists(datapackFolder)) {
            throw Exception("Datapack folder not found at $datapackFolder")
        }

        // Create .mcmeta file
        val mcmetaContent = createMcmetaJson(
            FormatReader.packFormat.dataVersion,
            "Defcon datapack"
        )
        Files.write(Paths.get("$tempPath/pack.mcmeta"), mcmetaContent.toByteArray())

        // Write biomes
        writeBiomes()
    }

    override fun onMove() {
        NotificationManager.addNotification(
            messageForServerRestart,
            NotificationType.WARNING
        )
    }

    /**
     * Writes biome JSON files to the datapack.
     */
    private fun writeBiomes() {
        try {
            // Get all biomes from BiomeRegister
            val biomes = BiomeRegister().getBiomes()

            for (biome in biomes) {
                val biomePath = Paths.get("$tempBiomesPath/${biome.key.value()}.json")
                val biomeJson = createBiomeJson(biome)

                Files.write(biomePath, biomeJson.toJSONString().toByteArray())
            }
        } catch (e: Exception) {
            info("Error writing biomes: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Creates a JSON object for a custom biome.
     */
    private fun createBiomeJson(biome: CustomBiome): JSONObject {
        return JSONObject().apply {
            put("temperature", biome.temperature)
            put("downfall", biome.downfall)
            put("precipitation", biome.precipitation.name.lowercase(Locale.getDefault()))
            put("temperature_modifier", biome.temperatureModifier.name.lowercase(Locale.getDefault()))
            put("has_precipitation", biome.hasPrecipitation)
            put("effects", createBiomeEffectsJson(biome.effects))
            put("spawners", JSONObject()) // TODO: Add spawners
            put("spawn_costs", JSONObject()) // TODO: Add spawn costs

            // Handle different versions
            put(
                "carvers",
                if (versionGreaterOrEqualThan("1.21.2")) JSONArray() else JSONObject()
            )

            put("features", JSONArray()) // TODO: Add features
        }
    }

    /**
     * Creates a JSON object for biome effects.
     */
    private fun createBiomeEffectsJson(effects: BiomeEffects): JSONObject {
        return JSONObject().apply {
            // Basic colors
            put("sky_color", effects.skyColor)
            put("fog_color", effects.fogColor)
            put("water_color", effects.waterColor)
            put("water_fog_color", effects.waterFogColor)

            // Mood sound
            effects.moodSound?.let { mood ->
                put("mood_sound", JSONObject().apply {
                    put("sound", mood.sound)
                    put("tick_delay", mood.tickDelay)
                    put("block_search_extent", mood.blockSearchExtent)
                    put("offset", mood.offset)
                })
            }

            // Additional sound
            effects.additionalSound?.let { additionalSound ->
                put("additional_sound", JSONObject().apply {
                    put("sound", additionalSound.sound)
                    put("tick_chance", additionalSound.tickChance)
                })
            }

            // Particle
            effects.particle?.let { particle ->
                put("particle", JSONObject().apply {
                    put("probability", particle.probability)
                    put("options", JSONObject().apply {
                        put("type", "minecraft:${particle.particle.name.lowercase(Locale.getDefault())}")

                        // Optional particle properties
                        particle.color?.let { put("color", it) }
                        particle.size?.let { put("size", it) }
                        particle.material?.let {
                            put("material", "minecraft:${it.name.lowercase(Locale.getDefault())}")
                        }
                        particle.fromColor?.let { put("from_color", it) }
                        particle.toColor?.let { put("to_color", it) }
                    })
                })
            }

            // Music
            effects.music?.let { music ->
                put("music", JSONObject().apply {
                    put("sound", music.sound)
                    put("min_delay", music.minDelay)
                    put("max_delay", music.maxDelay)
                    put("replace_current_music", music.replaceCurrentMusic)
                })
            }
        }
    }
}