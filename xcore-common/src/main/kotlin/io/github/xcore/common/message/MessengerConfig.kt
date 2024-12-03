package io.github.xcore.common.message

sealed interface MessengerConfig {
    data object Local : MessengerConfig
}
