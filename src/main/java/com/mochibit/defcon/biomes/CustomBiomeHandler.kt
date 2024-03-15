package com.mochibit.defcon.biomes


import com.mojang.serialization.Lifecycle
import net.minecraft.core.*
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.PacketPlayOutMap
import net.minecraft.network.protocol.game.PacketPlayOutUnloadChunk
import net.minecraft.resources.MinecraftKey
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.biome.*
import org.bukkit.*
import org.bukkit.craftbukkit.v1_19_R2.CraftChunk
import org.bukkit.craftbukkit.v1_19_R2.CraftServer
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld
import org.bukkit.craftbukkit.v1_19_R2.entity.CraftPlayer
import org.bukkit.entity.Player
import java.lang.reflect.Field
import java.util.*
import kotlin.math.abs


class CustomBiomeHandler {
    // TODO : WRAPPER FOR NMS STUFF ( for version compatibility )
    companion object {
        val server = Bukkit.getServer()
        val craftServer = server as CraftServer
        val dedicatedServer = craftServer.server

        private fun getBiomeRegistry(): IRegistry<BiomeBase> {
            return dedicatedServer.aW().d(Registries.al)
        }

        private fun getBiomeRegistryWritable(): IRegistryWritable<BiomeBase> {
            return getBiomeRegistry() as IRegistryWritable<BiomeBase>
        }


        fun registerBiome() {
            //  Testing code
            val newKey: ResourceKey<BiomeBase> = ResourceKey.a(
                Registries.al,
                MinecraftKey("defcon", "burning_air")
            )

            val newBiomeBuilder = BiomeBase.a()


            //Inject into the biome registry
            //al is BIOMES
            //aW is registryAccess
            //d is registryOrThrow
            val registryWriteable = getBiomeRegistryWritable()

            // Create a new biome
            val forestBiome = registryWriteable.a(Biomes.i)

            if (forestBiome != null) {
                //c is precipitation
                newBiomeBuilder.a(forestBiome.c())
                // K is the mob settings
                val biomeSettingMobsField = BiomeBase::class.java.getDeclaredField("k")
                biomeSettingMobsField.isAccessible = true
                val biomeSettingMobs = biomeSettingMobsField[forestBiome] as BiomeSettingsMobs
                newBiomeBuilder.a(biomeSettingMobs)

                //j is generationSettings
                val biomeSettingGenField = BiomeBase::class.java.getDeclaredField("j")
                biomeSettingGenField.isAccessible = true
                val biomeSettingGen = biomeSettingGenField[forestBiome] as BiomeSettingsGeneration
                newBiomeBuilder.a(biomeSettingGen);
            }

            newBiomeBuilder.a(0.2F); //Depth of biome
            newBiomeBuilder.b(0.05F); //Scale of biome
            // Temperature modifier
            newBiomeBuilder.a(BiomeBase.TemperatureModifier.a); //BiomeBase.TemperatureModifier.a will make your biome normal, BiomeBase.TemperatureModifier.b will make your biome frozen


            val newFog = BiomeFog.a()
            newFog.a(BiomeFog.GrassColor.a) //This doesn't affect the actual final grass color, just leave this line as it is or you will get errors

            //fogcolor (set to red)
            newFog.a(0x00FF0000)
            //water color i is getWaterColor
            if (forestBiome != null) {
                newFog.b(forestBiome.k())
            }
            //water fog color j is getWaterFogColor
            if (forestBiome != null) {
                newFog.c(forestBiome.l())
            }
            //sky color ( set to red )
            newFog.d(0x00FF0000)

            newBiomeBuilder.a(newFog.a());

            val newBiome = newBiomeBuilder.a()


            //Unfreeze Biome Registry
            var frozen: Field = RegistryMaterials::class.java.getDeclaredField("l")
            frozen.setAccessible(true)
            frozen.set(registryWriteable, false)

            //Inject unregisteredIntrusiveHolders with a new map to allow intrusive holders
            //m is unregisteredIntrusiveHolders
            val unregisteredIntrusiveHolders = RegistryMaterials::class.java.getDeclaredField("m")
            unregisteredIntrusiveHolders.isAccessible = true
            unregisteredIntrusiveHolders[registryWriteable] = IdentityHashMap<Any, Any>()

            // Register the new biome
            registryWriteable.f(newBiome)
            // Register the new biome key
            registryWriteable.a(newKey, newBiome, Lifecycle.stable())


            //Make unregisteredIntrusiveHolders null again to remove potential for undefined behaviour
            unregisteredIntrusiveHolders[registryWriteable] = null


            //Refreeze biome registry
            frozen = RegistryMaterials::class.java.getDeclaredField("l")
            frozen.isAccessible = true
            frozen[registryWriteable] = true

        }

        private fun <T> getRegistry(key: ResourceKey<IRegistry<T>>): RegistryMaterials<T> {
            val server = (Bukkit.getServer() as CraftServer).server
            return server.aW().d(key) as RegistryMaterials<T>
        }

        private fun inChunkViewDistance(player: Player, chunk: Chunk): Boolean {
            val playerLocation: Location = player.getLocation()

            val viewDistance = Bukkit.getViewDistance()
            val playerChunkX = playerLocation.chunk.x
            val playerChunkZ = playerLocation.chunk.z

            val targetChunkX = chunk.x
            val targetChunkZ = chunk.z

            val deltaX = abs((playerChunkX - targetChunkX).toDouble()).toInt()
            val deltaZ = abs((playerChunkZ - targetChunkZ).toDouble()).toInt()

            return deltaX <= viewDistance && deltaZ <= viewDistance
        }

        fun getPlayersInDistance(chunk: Chunk): List<org.bukkit.entity.Player> {
            val world: World = chunk.world

            return world.getPlayers().stream()
                .filter { player -> inChunkViewDistance(player, chunk) }
                .toList()
        }

        fun setAirFlameBiome(chunk: Chunk) {

            val accessor: RegionAccessor = chunk.world
            val key: NamespacedKey = NamespacedKey("defcon", "burning_air")

            val unsafe = Bukkit.getUnsafe();
            val minX = chunk.x shl 4
            val maxX = minX + 16

            val minZ = chunk.z shl 4
            val maxZ = minZ + 16


            for (x in minX until maxX) {
                for (y in chunk.world.minHeight until chunk.world.maxHeight) {
                    for (z in minZ until maxZ) {
                        // Set the biome of each block to the custom biome
                        unsafe.setBiomeKey(accessor, x, y, z, key)
                    }
                }
            }

            // Update the chunk to reflect the changes

            // Get players in the chunk
            val players = chunk.entities.filterIsInstance<Player>()
            // Send the chunk update to the players
            players.forEach { player ->
                // unload packet
                val unloadPacket = PacketPlayOutUnloadChunk(chunk.x, chunk.z)
                (player as CraftPlayer).handle.b.a(unloadPacket)
            }
            val nmsWorld = (chunk.world as CraftWorld).handle
            nmsWorld.k().a.a((chunk as CraftChunk).handle)

        }
    }
}