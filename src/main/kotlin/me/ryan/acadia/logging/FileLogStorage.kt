package me.ryan.acadia.logging

import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.logging.LogEntryFormatter.toJson
import me.ryan.acadia.logging.entity.LogEntry
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

    override fun store(entry: LogEntry) {
        val jsonLine = entry.toJson()

        try {
            val file = File(loggingProperties.file.path)
            file.parentFile?.mkdirs()

            PrintWriter(FileWriter(file, true)).use { writer ->
                writer.println(jsonLine)
            }
        } catch (e: Exception) {
            logger.error("Failed to write log to file: ${e.message}", e)
        }
        // Console output is handled by CompositeLogStorage to avoid duplicate logging.
    }
}
