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

package me.mochibit.defcon.commands.definitions

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.Dispatchers
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.commands.CommandInfo
import me.mochibit.defcon.commands.GenericCommand
import me.mochibit.defcon.events.plugin.geiger.GeigerDetectEvent
import me.mochibit.defcon.threading.scheduling.interval
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.seconds

@Suppress("UnstableApiUsage")
@CommandInfo(
    name = "debug",
    permission = "defcon.admin",
    adminOnly = true,
    requiresPlayer = false,
    description = "Debug command for testing purposes",
    usage = "/defcon debug <subcommand>"
)
class DebugCommand : GenericCommand() {
    override fun getArguments(): ArgumentBuilder<CommandSourceStack, *>? {
        return Commands
            .literal("randomRadiations")
            .executes(this::randomRadiation)
    }


    private fun randomRadiation(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender as? Player ?: return 0
        val radiationLevel = (1..100).random().toDouble()

        val job = interval(1.seconds) {
            val geigerDetectEvent = GeigerDetectEvent(sender, radiationLevel)
            sender.server.pluginManager.callEvent(geigerDetectEvent)
        }

        runLater(30.seconds) {
            job.close()
        }

        return Command.SINGLE_SUCCESS
    }
}