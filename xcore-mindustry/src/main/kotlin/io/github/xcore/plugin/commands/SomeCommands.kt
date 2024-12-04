package io.github.xcore.plugin.commands

import io.github.xcore.common.command.annotation.Command
import io.github.xcore.plugin.XCorePlugin
import io.github.xcore.plugin.command.MindustryActor

class SomeCommands {

    @Command("discord")
    fun onDiscordCommand(actor: MindustryActor) {
        actor.sender.audience.openURI(XCorePlugin.DISCORD_LINK)
    }
}