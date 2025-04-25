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

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger.info
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.enums.ConfigurationStorage
import java.nio.file.Files
import java.nio.file.Path

object ResourcePackServer {
    private var resourcePackPath: Path? = null
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    val port: Int
        get() = try {
            PluginConfiguration.get(ConfigurationStorage.Config).config.getInt("resource_pack_server_port")
        } catch (e: Exception) {
            Defcon.Logger.err("Failed to get resource pack server port from config, using default port 8080")
            8080
        }

    fun updatePackPath(path: Path) {
        resourcePackPath = path
    }

    fun startServer() {
        if (server != null) {
            Defcon.Logger.warn("Server is already running")
            return
        }

        val embeddedServer = embeddedServer(Netty, port = port) {
            routing {
                get("/resourcepack.zip") {
                    val path = resourcePackPath
                    if (path == null || !Files.exists(path)) {
                        call.respond(HttpStatusCode.NotFound, "No resource pack set")
                        return@get
                    }
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            "resourcepack.zip"
                        ).toString()
                    )
                    call.respondFile(path.toFile())
                }
            }
        }

        server = embeddedServer.start(wait = false)

        Runtime.getRuntime().addShutdownHook(Thread {
            stopServer()
        })

        info("Resource pack server started on port $port")
    }

    fun stopServer() {
        server?.stop(1000, 2000)
        server = null
    }
}
