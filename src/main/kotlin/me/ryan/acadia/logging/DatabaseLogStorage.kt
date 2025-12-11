package me.ryan.acadia.logging

import me.ryan.acadia.logging.entity.RequestLogEntry
import me.ryan.acadia.logging.repository.RequestLogRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["storage"], havingValue = "db")
class DatabaseLogStorage(
    private val requestLogRepository: RequestLogRepository,
) : LogStorage {
    private val logger = LoggerFactory.getLogger(DatabaseLogStorage::class.java)

    override fun store(entry: RequestLogEntry) {
        try {
            requestLogRepository.save(entry)
            logger.debug(
                "Stored request log: {} {} {}",
                entry.method,
                entry.path,
                entry.requestId,
            )
        } catch (e: Exception) {
            logger.error("Failed to store log in database: ${e.message}", e)
        }
    }
}
