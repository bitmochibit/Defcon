/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.mochibit.defcon.server

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger
import me.mochibit.defcon.Defcon.Logger.err
import me.mochibit.defcon.Defcon.Logger.info
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.enums.ConfigurationStorage
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

object ResourcePackServer {
    private var resourcePackPath: Path? = null
    private var serverSocket: ServerSocket? = null
    private var running = false
    private var serverJob: Job? = null

    val port: Int
        get() = try {
            PluginConfiguration.get(ConfigurationStorage.Config).config.getInt("resource_pack_server_port")
        } catch (e: Exception) {
            err("Failed to get resource pack server port from config, using default port 8080")
            8080
        }

    fun updatePackPath(path: Path) {
        resourcePackPath = path
    }

    fun startServer() {
        if (running) {
            Logger.warn("Server is already running")
            return
        }

        // Launch the server in a coroutine using the IO dispatcher
        serverJob = Defcon.instance.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                running = true

                info("Resource pack server started on port $port")

                while (running) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        // Launch a new coroutine for each client
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (running) {
                            err("Error accepting client connection: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                err("Failed to start resource pack server: ${e.message}")
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            stopServer()
        })
    }

    private suspend fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.use { socket ->
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()

                // Read HTTP request header
                val requestLine = input.readLine() ?: return
                if (!requestLine.startsWith("GET /resourcepack.zip")) {
                    sendNotFound(output)
                    return
                }

                // Skip the rest of the headers
                var line: String?
                do {
                    line = input.readLine()
                } while (!line.isNullOrEmpty())

                // Check if we have a resource pack
                val path = resourcePackPath
                if (path == null || !Files.exists(path)) {
                    sendNotFound(output)
                    return
                }

                // Send resource pack
                sendResourcePack(output, path)
            }
        } catch (e: Exception) {
            err("Error handling client request: ${e.message}")
        }
    }

    private fun sendNotFound(output: OutputStream) {
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                "No resource pack set"

        output.write(response.toByteArray())
        output.flush()
    }

    private suspend fun sendResourcePack(output: OutputStream, path: Path) {
        val fileSize = withContext(Dispatchers.IO) {
            Files.size(path)
        }
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/zip\r\n" +
                "Content-Length: $fileSize\r\n" +
                "Content-Disposition: attachment; filename=\"resourcepack.zip\"\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        withContext(Dispatchers.IO) {
            output.write(headers.toByteArray())
        }

        // Send file content with coroutines
        withContext(Dispatchers.IO) {
            FileInputStream(path.toFile()).use { fileInput ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }

        withContext(Dispatchers.IO) {
            output.flush()
        }
    }

    fun stopServer() {
        if (!running) return

        running = false
        serverJob?.cancel()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            err("Error stopping resource pack server: ${e.message}")
        }

        serverSocket = null
        info("Resource pack server stopped")
    }
}