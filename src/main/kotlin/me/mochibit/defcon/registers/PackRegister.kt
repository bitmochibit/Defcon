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

import me.mochibit.defcon.Defcon.Logger.info
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.nameWithoutExtension

/**
 * Base class for creating resource and data packs.
 * @param zipDestination Whether to create a zip file of the pack.
 */
abstract class PackRegister(private val zipDestination: Boolean = false) {

    abstract val tempPath: Path
    abstract val destinationPath: Path?

    private var packRegistered = false
    val isPackRegistered get() = packRegistered
    /**
     * Creates the pack and moves it to its destination.
     */
    fun register() {
        // Clean up any existing temp directory
        if (Files.exists(tempPath)) {
            tempPath.toFile().deleteRecursively()
        }

        Files.createDirectories(tempPath)

        // Write the pack into files
        write()

        // Move to destination if specified
        val moved = destinationPath?.let {
            info("Moving pack to $it")

            // Skip if content is identical
            if (getFolderHash(tempPath) == getFolderHash(it)) {
                return@let false
            }

            // Remove existing destination
            if (Files.exists(it)) {
                it.toFile().deleteRecursively()
            }

            // Move from temp to destination
            Files.move(tempPath, it)
            info("Pack moved to $it")

            return@let true
        } ?: false

        if (moved) onMove()

        // Create zip file if requested
        if (zipDestination) {
            destinationPath?.let {
                val zipPath = Paths.get("${it}.zip")
                if (Files.exists(zipPath)) {
                    Files.delete(zipPath)
                }
                zipFolder(it, zipPath, true)

                onPackCreated(zipPath)

                it.toFile().deleteRecursively()
            }
        } else {
            destinationPath?.let {
                onPackCreated(it)
            }
        }

        tempPath.toFile().deleteRecursively()
        packRegistered = true
    }

    /**
     * Calculate a hash of the folder contents for comparison.
     */
    private fun getFolderHash(pathToHash: Path): Int {
        return if (Files.exists(pathToHash))
            Files.walk(pathToHash)
                .filter { path -> Files.isRegularFile(path) }
                .map { path -> path.toFile().readText() }
                .reduce { acc, s -> acc + s }
                .hashCode()
        else 0
    }

    /**
     * Abstract method to be implemented by derived classes.
     * Writes the pack content to the temp directory.
     */
    protected abstract fun write()

    open fun onPackCreated(finalPath: Path) {}

    open fun onMove() {}

    /**
     * Creates a zip file of the specified folder.
     *
     * @param folderPath Path to the folder to zip
     * @param zipPath Path where the zip file will be created
     * @param zipFolderPathContent Whether to zip the folder content directly
     */
    private fun zipFolder(folderPath: Path, zipPath: Path, zipFolderPathContent: Boolean = false) {
        FileOutputStream(zipPath.toString()).use { fos ->
            ZipOutputStream(fos).use { zipOut ->
                val fileToZip = folderPath.toFile()

                if (fileToZip.isDirectory) {
                    if (zipFolderPathContent) {
                        fileToZip.listFiles()?.forEach { childFile ->
                            zipFile(childFile, childFile.name, zipOut)
                        }
                    } else {
                        zipFile(fileToZip, fileToZip.name, zipOut)
                    }
                } else {
                    zipFile(fileToZip, fileToZip.name, zipOut)
                }
            }
        }
    }

    /**
     * Helper method to add a file or directory to a zip file.
     */
    private fun zipFile(fileToZip: File, fileName: String, zipOut: ZipOutputStream) {
        if (fileToZip.isHidden) {
            return
        }

        if (fileToZip.isDirectory) {
            val entryName = if (fileName.endsWith("/")) fileName else "$fileName/"
            zipOut.putNextEntry(ZipEntry(entryName))
            zipOut.closeEntry()

            fileToZip.listFiles()?.forEach { childFile ->
                zipFile(childFile, "$fileName/${childFile.name}", zipOut)
            }
            return
        }

        FileInputStream(fileToZip).use { fis ->
            val zipEntry = ZipEntry(fileName)
            zipOut.putNextEntry(zipEntry)

            val buffer = ByteArray(8192)
            var length: Int

            while (fis.read(buffer).also { length = it } >= 0) {
                zipOut.write(buffer, 0, length)
            }
        }
    }

    /**
     * Helper method to copy a resource folder to a target path.
     */
    protected fun copyFoldersFromResource(
        resourceFolderPath: String,
        targetPath: Path,
        excludedPaths: Set<String> = emptySet()
    ) {
        val jarFile = File(javaClass.protectionDomain.codeSource.location.path)

        JarFile(jarFile).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()

                // Skip excluded paths
                if (excludedPaths.any { entry.name.startsWith(it) }) {
                    continue
                }

                // Copy files from the resource folder to the target
                if (entry.name.startsWith(resourceFolderPath)) {
                    val initialPath = entry.name.substring(resourceFolderPath.length)
                    val target = targetPath.resolve(initialPath)

                    if (entry.isDirectory) {
                        info("Creating directory $target")
                        Files.createDirectories(target)
                    } else {
                        jar.getInputStream(entry).use { input ->
                            Files.createDirectories(target.parent)
                            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a standard .mcmeta file for the pack.
     *
     * @param formatVersion The pack format version
     * @param description The pack description
     * @return The JSON string for the .mcmeta file
     */
    protected fun createMcmetaJson(formatVersion: Int, description: String): String {
        return """
        {
            "pack": {
                "pack_format": $formatVersion,
                "description": "$description"
            }
        }
        """.trimIndent()
    }
}