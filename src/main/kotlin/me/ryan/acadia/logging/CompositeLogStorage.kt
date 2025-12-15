package me.ryan.acadia.logging

import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.config.LoggingProperties.StorageType
import me.ryan.acadia.logging.entity.LogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Composite log storage that always outputs to console and optionally to database.
 * Console logging is always performed regardless of storage configuration.
 * Database storage is only performed when storage type is DB.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
class CompositeLogStorage(
    private val consoleLogStorage: ConsoleLogStorage,
    private val databaseLogStorage: DatabaseLogStorage?,
    private val loggingProperties: LoggingProperties,
) : LogStorage {
    override fun store(entry: LogEntry) {
        // Always log to console
        consoleLogStorage.store(entry)

        // Conditionally store to database
        if (loggingProperties.storage == StorageType.DB) {
            databaseLogStorage?.store(entry)
        }
    }
}
