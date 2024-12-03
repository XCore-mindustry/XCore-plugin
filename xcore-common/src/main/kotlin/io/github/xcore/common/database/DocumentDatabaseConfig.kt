package io.github.xcore.common.database

sealed interface DocumentDatabaseConfig {
    data object Local : DocumentDatabaseConfig
    data class MongoDB(val uri: String, val database: String = "xcore") : DocumentDatabaseConfig
}
