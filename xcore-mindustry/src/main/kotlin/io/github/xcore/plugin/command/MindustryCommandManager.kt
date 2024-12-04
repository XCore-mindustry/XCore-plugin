package io.github.xcore.plugin.command

import arc.util.CommandHandler
import com.xpdustry.distributor.api.command.CommandSender
import io.github.xcore.common.command.CommandManager
import mindustry.Vars
import mindustry.gen.Player
import mindustry.server.ServerControl
import java.util.ArrayDeque
import java.util.regex.Pattern
import kotlin.reflect.full.hasAnnotation

class MindustryCommandManager : CommandManager<MindustryActor>() {

    override fun onCommandRegistered(command: AnnotationCommand<MindustryActor>) {
        var marked = false
        if (command.function.hasAnnotation<ClientSide>()) {
            marked = true
            registerTo(command, Vars.netServer.clientCommands)
        }
        if (command.function.hasAnnotation<ServerSide>()) {
            marked = true
            registerTo(command, ServerControl.instance.handler)
        }
        if (!marked) {
            error("Command ${command.name} is not marked as client-side or server-side.")
        }
    }

    private fun registerTo(command: AnnotationCommand<MindustryActor>, handler: CommandHandler) {
        handler.register<Player?>(command.name, "[args...]", "unknown") { args, player ->
            val sender = if (player == null) CommandSender.server() else CommandSender.player(player)
            val actor = MindustryActor(sender)
            val queue = ArrayDeque<String>()
            val matchers = ARGUMENT_SPLITTER.matcher(args.firstOrNull() ?: "")
            while (matchers.find()) {
                val argument = matchers.group("quoted")?.trim('"') ?: matchers.group("unquoted")!!
                if (argument.isNotBlank()) queue += argument
            }
            try {
                val invocation = command.arguments.associate { argument ->
                    if (argument.resolver.size > queue.size) {
                        actor.error("Not enough arguments.")
                        return@register
                    }
                    val result = argument.resolver.resolve(actor, queue)
                    if (result.isFailure) {
                        actor.error(result.failure)
                        return@register
                    }
                    argument.parameter to result
                }
                command.function.callBy(invocation)
            } catch (e: Exception) {
                actor.error("An error occurred while executing the command.")
            }
        }
    }

    companion object {
        private val ARGUMENT_SPLITTER = Pattern.compile("(?<quoted>\"[\\w\\W]+\")|(?<unquoted>\\w+)")
    }
}