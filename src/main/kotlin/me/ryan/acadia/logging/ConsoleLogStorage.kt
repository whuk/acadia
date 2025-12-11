package me.ryan.acadia.logging

import me.ryan.acadia.logging.LogEntryFormatter.toJson
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "gateway.logging",
    name = ["storage"],
    havingValue = "none",
    matchIfMissing = true,
)
class ConsoleLogStorage : LogStorage {
    private val logger = LoggerFactory.getLogger(ConsoleLogStorage::class.java)

    override fun store(entry: RequestLogEntry) {
        logger.info(entry.toJson())
    }
}
