package io.github.xcore.plugin.command

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.TranslatableComponent
import com.xpdustry.distributor.api.translation.TranslationArguments
import io.github.xcore.common.command.Actor
import io.github.xcore.common.translation.Translatable

data class MindustryActor(val sender: CommandSender) : Actor {
    override fun reply(message: String) {
        sender.reply(message)
    }

    override fun reply(translatable: Translatable) {
        sender.reply(TranslatableComponent.translatable(translatable.key, TranslationArguments.named(translatable.parameters)))
    }

    override fun error(message: String) {
        sender.error(message)
    }

    override fun error(translatable: Translatable) {
        sender.error(TranslatableComponent.translatable(translatable.key, TranslationArguments.named(translatable.parameters)))
    }
}