import net.minecrell.pluginyml.paper.PaperPluginDescription
import java.time.Instant

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("de.eldoria.plugin-yml.paper") version "0.7.1"
}

group = "me.mochibit"
version = "1.3.5-SNAPSHOT"

// Project metadata
description = "A plugin that adds nuclear energy, along with its advantages and dangers"

// Dependency versions - centralized for easier management
object Versions {
    const val KOTLIN = "2.1.20"
    const val PAPER_API = "1.21.4-R0.1-SNAPSHOT"
    const val PROTOCOL_LIB_PLUGIN = "5.3.0"
    const val CUSTOM_BLOCK_DATA = "2.2.4"
    const val GSON = "2.10.1"
    const val JUNIT = "5.10.1"
    const val MOCKITO = "5.8.0"
    const val JVM = 21
    val JAVA_VERSION = JavaVersion.VERSION_21
}

// Output configuration
val outputPluginDirectory: String = project.findProperty("outputDir")?.toString()
    ?: layout.buildDirectory.dir("libs").get().asFile.path
logger.lifecycle("Output directory: $outputPluginDirectory")

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.JVM))
    }
}

// Java configuration
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.JVM))
    }
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = Versions.JAVA_VERSION
    targetCompatibility = Versions.JAVA_VERSION
}

// Plugin.yml generation
paper {
    main = "me.mochibit.defcon.Defcon"
    apiVersion = "1.19"
    name = "Defcon"
    version = project.version.toString()
    description = project.description
    authors = listOf("MochiBit")
    website = "https://github.com/mochibit/defcon"

    serverDependencies {
        register("ProtocolLib") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = true
        }
    }

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
    maven("https://repo.dmulloy2.net/repository/public/") // ProtocolLib
    maven("https://hub.jeff-media.com/nexus/repository/jeff-media-public/") // CustomBlockData
}

dependencies {
    // Server API - compileOnly to avoid bundling
    compileOnly("io.papermc.paper:paper-api:${Versions.PAPER_API}")
    compileOnly("com.comphenix.protocol", "ProtocolLib", Versions.PROTOCOL_LIB_PLUGIN)

    // Libraries to be shaded
    implementation("com.jeff-media:custom-block-data:${Versions.CUSTOM_BLOCK_DATA}")
    implementation("org.reflections:reflections:0.10.2") {
        exclude(group = "org.slf4j")
        exclude(group = "com.google.code.findbugs")
    }
    implementation(kotlin("stdlib-jdk8", Versions.KOTLIN))
    implementation(kotlin("reflect", Versions.KOTLIN))
    implementation("com.google.code.gson:gson:${Versions.GSON}")

    // Testing
    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation("org.junit.jupiter:junit-jupiter:${Versions.JUNIT}")
    testImplementation("org.mockito:mockito-core:${Versions.MOCKITO}")
    testImplementation("io.papermc.paper:paper-api:${Versions.PAPER_API}")
}

// Kotlin compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.JVM.toString()))
        freeCompilerArgs.addAll("-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
    }
}

// Java compilation
tasks.withType<JavaCompile>().configureEach {
    options.apply {
        encoding = "UTF-8"
        release.set(Versions.JVM)
    }
}

// Testing configuration
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // Parallel test execution for faster builds
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

// Server test configuration
tasks.runServer {
    minecraftVersion("1.20.2")

    downloadPlugins {
        github("dmulloy2", "ProtocolLib", Versions.PROTOCOL_LIB_PLUGIN, "ProtocolLib.jar")
    }
}

// JAR configuration
tasks.jar {
    archiveBaseName.set("Defcon")
    archiveVersion.set(project.version.toString())
    enabled = false // Disable default jar
}

// Shadow JAR configuration
tasks.shadowJar {
    archiveBaseName.set("Defcon")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

//  Relocate dependencies
    relocate("com.jeff_media.customblockdata", "me.mochibit.defcon.lib.customblockdata")
//    relocate("org.reflections", "me.mochibit.defcon.lib.reflections")
//    relocate("com.google.gson", "me.mochibit.defcon.lib.gson")
//    relocate("javassist", "me.mochibit.defcon.lib.javassist")

//  Minimize JAR size
    minimize {
        exclude(dependency("org.jetbrains.kotlin:.*"))
    }

    // Exclude unnecessary files
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")

    // Add build timestamp manifest entry
    manifest {
        attributes(
            "Built-By" to System.getProperty("user.name"),
            "Build-Timestamp" to Instant.now().toString(),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

// Default artifact
artifacts {
    archives(tasks.shadowJar)
}

// Make shadowJar part of the build process
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Install plugin task
tasks.register<Copy>("installPlugin") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into(file(outputPluginDirectory))
    doLast {
        logger.lifecycle("Plugin installed to: $outputPluginDirectory/${tasks.shadowJar.get().archiveFileName.get()}")
    }
}

// Clean, build and install task
tasks.register("cleanBuildInstall") {
    group = "build"
    description = "Cleans the project, builds it, and installs the plugin to the output directory"
    dependsOn(tasks.clean, "installPlugin")
}

// Configure Gradle to use parallel execution where possible
gradle.taskGraph.whenReady {
    System.setProperty("org.gradle.parallel", "true")
}