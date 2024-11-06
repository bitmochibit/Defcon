plugins {
    kotlin("jvm") version "1.9.22"
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2" // for shading dependencies
}

group = "me.mochibit"
version = "1.4-SNAPSHOT"
description = "A plugin that adds nuclear energy, along with its advantages and dangers"
java.sourceCompatibility = JavaVersion.VERSION_17

// Define variables for commonly used values
val kotlinVersion = "1.9.22"
val jvmTargetVersion = "17"
val paperApiVersion = "1.20.2-R0.1-SNAPSHOT"
val protocolLibVersion = "5.2.0-SNAPSHOT"
val customBlockDataVersion = "2.2.3"
val gsonVersion = "2.10"

// Get the output directory from system properties or use default `build/libs`
val outputPluginDirectory = project.findProperty("outputDir")?.toString() ?: layout.buildDirectory.dir("libs").get().asFile.path
println("Output directory: $outputPluginDirectory")

repositories {
    mavenCentral()
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://hub.jeff-media.com/nexus/repository/jeff-media-public/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    implementation("io.papermc:paperlib:1.0.7")

    // Spigot API
    compileOnly("org.spigotmc:spigot-api:$paperApiVersion")

    // CustomBlockData
    implementation("com.jeff-media:custom-block-data:$customBlockDataVersion")

    // ProtocolLib
    compileOnly("com.comphenix.protocol:ProtocolLib:$protocolLibVersion")

    // WorldGuard
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.8-SNAPSHOT")

    // Reflections
    implementation("org.reflections:reflections:0.10.2")

    // Kotlin Standard Library
    implementation(kotlin("stdlib-jdk8", kotlinVersion))

    // Kotlin Test Library
    testImplementation(kotlin("test", kotlinVersion))

    // Gson
    implementation("com.google.code.gson:gson:$gsonVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = jvmTargetVersion
        apiVersion = "1.9"
        languageVersion = "1.9"
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = jvmTargetVersion
    targetCompatibility = jvmTargetVersion
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("Defcon")
    archiveVersion.set(version.toString())
}

tasks.shadowJar {
    // Apply relocations to avoid classpath conflicts
    relocate("io.papermc.lib", "me.mochibit.defcon.internalapi.paperlib")
    relocate("com.jeff_media.customblockdata", "me.mochibit.defcon.internalapi.customblockdata")
}

// Ensure shadowJar task runs when building the project
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Task to copy the built plugin to the specified directory
tasks.register<Copy>("installPlugin") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into(file(outputPluginDirectory))
}
