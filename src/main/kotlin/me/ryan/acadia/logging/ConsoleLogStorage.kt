package me.ryan.acadia.logging

import com.fasterxml.jackson.databind.ObjectMapper
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
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    override fun store(entry: RequestLogEntry) {
        val logData =
            buildMap {
                put("type", "REQUEST")
                put("timestamp", entry.timestamp.toString())
                put("requestId", entry.requestId)
                put("method", entry.method)
                put("path", entry.path)
                entry.queryParams?.let { put("queryParams", it) }
                entry.headers?.let { put("headers", it) }
            }
        logger.info(objectMapper.writeValueAsString(logData))
    }
}
