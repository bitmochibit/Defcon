import java.time.Instant

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.20"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("de.eldoria.plugin-yml.bukkit") version "0.7.1"
}

// Dependency versions - centralized for easier management
object Versions {
    const val KOTLIN = "2.2.0"
    const val KOTLINX_COROUTINES = "1.10.2"
    const val PAPER_API = "1.21.4-R0.1-SNAPSHOT"
    const val PACKET_EVENTS = "2.7.0"
    const val CUSTOM_BLOCK_DATA = "2.2.4"
    const val GSON = "2.10.1"
    const val JUNIT = "5.10.1"
    const val MOCKITO = "5.8.0"
    const val MOCKBUKKIT = "4.0.0"
    const val MCCOROUTINE = "2.22.0"
    const val REFLECTIONS = "0.10.2"
    const val JVM_TARGET = 23
}

object PacketEvents {
    const val PLATFORM = "spigot"
}

group = "me.mochibit"
version = "1.3.6-SNAPSHOT"
description = "A plugin that adds nuclear energy, along with its advantages and dangers"

// Output configuration
val outputPluginDirectory: String =
    project.findProperty("outputDir")?.toString()
        ?: layout.buildDirectory
            .dir("libs")
            .get()
            .asFile.path

logger.lifecycle("Output directory: $outputPluginDirectory")

// Kotlin configuration
kotlin {
    jvmToolchain(Versions.JVM_TARGET)

    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(Versions.JVM_TARGET.toString()),
        )
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xcontext-receivers", // Enable context receivers if needed
        )
    }
}

// Plugin.yml generation
bukkit {
    main = "me.mochibit.defcon.DefconPlugin"
    apiVersion = "1.21"
    name = "Defcon"
    version = project.version.toString()
    description = project.description
    authors = listOf("MochiBit")
    website = "https://github.com/mochibit/defcon"

    depend = listOf("packetevents")

    permissions {
        register("defcon.admin") {
            description = "Gives access to all Defcon admin commands"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://hub.jeff-media.com/nexus/repository/jeff-media-public/") // CustomBlockData
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    // Server API - compileOnly to avoid bundling
    compileOnly("io.papermc.paper:paper-api:${Versions.PAPER_API}")
    compileOnly("com.github.retrooper:packetevents-${PacketEvents.PLATFORM}:${Versions.PACKET_EVENTS}")

    library(kotlin("stdlib", Versions.KOTLIN))
    library(kotlin("reflect", Versions.KOTLIN))
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.KOTLINX_COROUTINES}")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${Versions.KOTLINX_COROUTINES}")
    library("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    library("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Coroutines for Minecraft
    library("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:${Versions.MCCOROUTINE}")
    library("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:${Versions.MCCOROUTINE}")

    // Utility libraries
    library("com.google.code.gson:gson:${Versions.GSON}")

    // Libraries to be shaded
    implementation("com.jeff-media:custom-block-data:${Versions.CUSTOM_BLOCK_DATA}")
    implementation("org.reflections:reflections:${Versions.REFLECTIONS}") {
        exclude(group = "org.slf4j")
        exclude(group = "com.google.code.findbugs")
    }

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${Versions.JUNIT}")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:${Versions.MOCKBUKKIT}")
    testImplementation("org.mockito:mockito-core:${Versions.MOCKITO}")
    testImplementation("io.papermc.paper:paper-api:${Versions.PAPER_API}")
}

// Testing configuration
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // JVM arguments for testing
    jvmArgs("-Xmx2G", "-XX:+UseG1GC")
}

// Server test configuration
tasks.runServer {
    minecraftVersion("1.21.4")

    downloadPlugins {
        github(
            "retrooper",
            "packetevents",
            "v${Versions.PACKET_EVENTS}",
            "packetevents-${PacketEvents.PLATFORM}-${Versions.PACKET_EVENTS}.jar",
        )
    }

    // JVM arguments for the test server
    jvmArgs("-Xmx4G", "-XX:+UseG1GC")
}

// Disable default jar task
tasks.jar {
    enabled = false
}

// Shadow JAR configuration
tasks.shadowJar {
    archiveBaseName.set("Defcon")
    archiveFileName.set("Defcon-$version.jar")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    // Relocate dependencies to avoid conflicts
    relocate("com.jeff_media.customblockdata", "me.mochibit.lib.customblockdata")
    relocate("org.reflections", "me.mochibit.lib.reflections")

    // Minimize JAR size but preserve Kotlin runtime
    minimize {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains.kotlinx:.*"))
    }

    // Exclude unnecessary files
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
    exclude("META-INF/maven/**")
    exclude("**/*.kotlin_metadata")
    exclude("**/*.kotlin_builtins")

    // Build information in manifest
    manifest {
        attributes(
            "Built-By" to System.getProperty("user.name"),
            "Build-Timestamp" to Instant.now().toString(),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Kotlin-Version" to Versions.KOTLIN,
        )
    }
}

// Make shadowJar the default artifact
artifacts {
    archives(tasks.shadowJar)
}

// Build process
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Install plugin task
tasks.register<Copy>("installPlugin") {
    group = "build"
    description = "Installs the built plugin to the output directory"
    dependsOn(tasks.shadowJar)

    from(tasks.shadowJar.get().archiveFile)
    into(file(outputPluginDirectory))

    doLast {
        val fileName =
            tasks.shadowJar
                .get()
                .archiveFileName
                .get()
        logger.lifecycle("Plugin installed to: $outputPluginDirectory/$fileName")
    }
}

// Clean, build and install task
tasks.register("cleanBuildInstall") {
    group = "build"
    description = "Cleans the project, builds it, and installs the plugin to the output directory"
    dependsOn(tasks.clean, "installPlugin")

    // Ensure proper task ordering
    tasks.clean.get().mustRunAfter("installPlugin")
}

// Performance optimization
gradle.taskGraph.whenReady {
    System.setProperty("org.gradle.parallel", "true")
    System.setProperty("org.gradle.configureondemand", "true")
    System.setProperty("org.gradle.caching", "true")
}
