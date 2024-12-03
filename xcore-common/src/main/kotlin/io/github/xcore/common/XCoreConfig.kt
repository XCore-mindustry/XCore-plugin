package io.github.xcore.common

import io.github.xcore.common.database.DocumentDatabaseConfig
import io.github.xcore.common.message.MessengerConfig

data class XCoreConfig(
    val messenger: MessengerConfig = MessengerConfig.Local,
    val database: DocumentDatabaseConfig = DocumentDatabaseConfig.Local
)

