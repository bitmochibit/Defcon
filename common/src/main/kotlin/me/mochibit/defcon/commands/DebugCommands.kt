package me.mochibit.defcon.commands

import com.mojang.brigadier.CommandDispatcher
import me.mochibit.defcon.commands.SetRecordUrlCommand.registerCommand
import me.mochibit.defcon.explosions.NuclearExplosion
import me.mochibit.defcon.foundation.info
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

sealed interface CommandEntry {
    fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        env: Commands.CommandSelection,
        context: CommandBuildContext,
    )

    companion object {
        fun registerAll(
            dispatcher: CommandDispatcher<CommandSourceStack>,
            env: Commands.CommandSelection,
            context: CommandBuildContext,
        ) {
            "Registering mod commands..".info()
            CommandEntry::class.sealedSubclasses.filter { CommandEntry::class.java.isAssignableFrom(it.java) }
                .map { it.objectInstance as CommandEntry }.forEach { _ -> registerCommand(dispatcher, env, context) }
        }
    }
}

object SetRecordUrlCommand : CommandEntry {
    override fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        env: Commands.CommandSelection,
        context: CommandBuildContext,
    ) {
        dispatcher.register(
            Commands.literal("defcon").requires { it.hasPermission(2) }.then(
                Commands.literal("nuke").executes { ctx ->
                    val source = ctx.source
                    val pos = BlockPos.containing(source.position)

                    NuclearExplosion(source.level, pos).trigger()
                    source.sendSuccess({ Component.literal("Triggered nuclear explosion at $pos") }, true)
                    1
                })
        )
    }
}