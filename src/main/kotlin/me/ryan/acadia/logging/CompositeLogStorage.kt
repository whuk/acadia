package me.ryan.acadia.logging

import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.config.LoggingProperties.StorageType
import me.ryan.acadia.logging.entity.LogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Composite log storage that always outputs to console and optionally to a file.
 * Console logging is always performed regardless of storage configuration.
 * File storage is only performed when storage type is FILE.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
class CompositeLogStorage(
    private val consoleLogStorage: ConsoleLogStorage,
    private val fileLogStorage: FileLogStorage?,
    private val loggingProperties: LoggingProperties,
) : LogStorage {
    override fun store(entry: LogEntry) {
        // Always log to console
        consoleLogStorage.store(entry)

        // Conditionally store to file
        if (loggingProperties.storage == StorageType.FILE) {
            fileLogStorage?.store(entry)
        }
    }
}
