package me.ryan.acadia.logging

import me.ryan.acadia.logging.entity.LogEntry
import me.ryan.acadia.logging.repository.LogRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["storage"], havingValue = "db")
class DatabaseLogStorage(
    private val logRepository: LogRepository,
) : LogStorage {
    private val logger = LoggerFactory.getLogger(DatabaseLogStorage::class.java)

    override fun store(entry: LogEntry) {
        try {
            logRepository.save(entry)
            logger.debug(
                "Stored {} log: requestId={}",
                entry.type,
                entry.requestId,
            )
        } catch (e: Exception) {
            logger.error("Failed to store log in database: ${e.message}", e)
        }
    }
}
