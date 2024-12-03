package io.github.xcore.common.message

import kotlinx.serialization.Serializable

@Serializable
sealed interface Message<R : @Serializable Any>
