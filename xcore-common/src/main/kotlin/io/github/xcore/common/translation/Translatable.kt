package io.github.xcore.common.translation

data class Translatable(val key: String, val parameters: Map<String, Any> = emptyMap()) {
    constructor(message: String, vararg parameters: Pair<String, Any>) : this(message, parameters.toMap())
}
