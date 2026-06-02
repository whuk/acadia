package me.ryan.acadia.logging

import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.logging.LogEntryFormatter.toJson
import me.ryan.acadia.logging.entity.LogEntry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["storage"], havingValue = "file")
class FileLogStorage(
    private val loggingProperties: LoggingProperties,
) : LogStorage,
    DisposableBean {
    private val logger = LoggerFactory.getLogger(FileLogStorage::class.java)
    private val lock = Any()

    // Single reusable writer opened lazily on first write. Avoids per-request
    // FileWriter open/close and mkdirs syscalls on the hot logging path.
    private var writer: BufferedWriter? = null

    override fun store(entry: LogEntry) {
        val jsonLine = entry.toJson()

        try {
            synchronized(lock) {
                val w = writer ?: openWriter().also { writer = it }
                w.write(jsonLine)
                w.newLine()
                w.flush() // line-level durability for request logs
            }
        } catch (e: Exception) {
            logger.error("Failed to write log to file: ${e.message}", e)
        }
        // Console output is handled by CompositeLogStorage to avoid duplicate logging.
    }

    private fun openWriter(): BufferedWriter {
        val file = File(loggingProperties.file.path)
        file.parentFile?.mkdirs()
        return BufferedWriter(FileWriter(file, true))
    }

    override fun destroy() {
        synchronized(lock) {
            writer?.close()
            writer = null
        }
    }
}
