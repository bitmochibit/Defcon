package me.mochibit.defcon.foundation.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.tterrag.registrate.providers.ProviderType
import me.mochibit.defcon.DefconMod.MOD_ID
import me.mochibit.defcon.ModRegistrate
import me.mochibit.defcon.foundation.err
import me.mochibit.defcon.foundation.info
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.RegistryPatchGenerator
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@EventBusSubscriber(modid = MOD_ID)
object DataGenerators {
    @SubscribeEvent
    @JvmStatic
    fun onGatherData(event: GatherDataEvent) {
        "Generating data for Defcon".info()
        "includeServer=${event.includeServer()}, includeClient=${event.includeClient()}".info()
        val generator = event.generator
        val output = generator.packOutput
        var lookupProvider = event.lookupProvider
        val existingFileHelper = event.existingFileHelper

        val generatedEntriesProvider = GeneratedEntriesProvider(output, lookupProvider)

        lookupProvider = generatedEntriesProvider.registryProvider
        generator.addProvider(true, generatedEntriesProvider)

//        if (event.includeClient()) {
//            generator.addProvider(
//                true,
//                EtherealRecordVisualModelProvider(output, existingFileHelper),
//            )
//        }
//
//        if (event.includeServer()) {
//            ModRecipeProvider.registerAllProcessRecipes(generator, output, lookUpProvider)
//        }
    }

    fun provideLang() {
        ModRegistrate.addDataGenerator(ProviderType.LANG) { provider ->
            val langConsumer: (String, String) -> Unit = { key, value ->
                provider.add(key, value)
            }

//            RecordType.EffectAttribute.provideLang(langConsumer)

            provideDefaultLang(langConsumer)

//            providePonderLang(langConsumer)
        }
    }

    private fun provideDefaultLang(consumer: (String, String) -> Unit) {
        val path = "assets/defcon/lang/default/en_us.json"
        val jsonElement =
            JsonResourceLoader.loadJsonResource(path)
                ?: throw IllegalStateException("Could not find default lang file: $path")
        val jsonObject = jsonElement.asJsonObject
        for (entry in jsonObject.entrySet()) {
            val key = entry.key
            val value = entry.value.asString
            consumer(key, value)
        }
    }

//    private fun providePonderLang(consumer: (String, String) -> Unit) {
//        // Register this since FMLClientSetupEvent does not run during datagen
//        PonderIndex.addPlugin(ModPonderPlugin())
//
//        PonderIndex.getLangAccess().provideLang(MOD_ID, consumer)
//    }
}

object JsonResourceLoader {
    private val gson = Gson()

    fun loadJsonResource(path: String): JsonElement? {
        return try {
            val inputStream = JsonResourceLoader::class.java.classLoader.getResourceAsStream(path)
            if (inputStream == null) {
                "Could not find resource: $path".err()
                return null
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = gson.fromJson(reader, JsonElement::class.java)
            reader.close()
            json
        } catch (e: Exception) {
            "Error loading JSON resource $path: ${e.message}".err()
            null
        }
    }
}