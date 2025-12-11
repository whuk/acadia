package me.ryan.acadia.logging

import com.fasterxml.jackson.databind.ObjectMapper
import me.ryan.acadia.config.LoggingProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["storage"], havingValue = "file")
class FileLogStorage(
    private val loggingProperties: LoggingProperties,
) : LogStorage {
    private val logger = LoggerFactory.getLogger(FileLogStorage::class.java)
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

        val jsonLine = objectMapper.writeValueAsString(logData)

        try {
            val file = File(loggingProperties.file.path)
            file.parentFile?.mkdirs()

            PrintWriter(FileWriter(file, true)).use { writer ->
                writer.println(jsonLine)
            }
        } catch (e: Exception) {
            logger.error("Failed to write log to file: ${e.message}", e)
        }

        // Also log to console
        logger.info(jsonLine)
    }
}
