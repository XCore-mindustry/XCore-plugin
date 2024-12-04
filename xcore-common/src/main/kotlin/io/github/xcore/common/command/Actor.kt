package io.github.xcore.common.command

import io.github.xcore.common.translation.Translatable

interface Actor {
    fun reply(message: String)
    fun reply(translatable: Translatable)
    fun error(message: String)
    fun error(translatable: Translatable)
}
