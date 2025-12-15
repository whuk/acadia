package me.ryan.acadia.logging

import me.ryan.acadia.logging.LogEntryFormatter.toJson
import me.ryan.acadia.logging.entity.LogEntry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
class ConsoleLogStorage : LogStorage {
    private val logger = LoggerFactory.getLogger(ConsoleLogStorage::class.java)

    override fun store(entry: LogEntry) {
        logger.info(entry.toJson())
    }
}
