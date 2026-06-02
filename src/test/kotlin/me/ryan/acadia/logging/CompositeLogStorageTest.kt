package me.ryan.acadia.logging

import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.config.LoggingProperties.StorageType
import me.ryan.acadia.logging.entity.LogEntry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant

class CompositeLogStorageTest {
    private val consoleLogStorage = mock<ConsoleLogStorage>()
    private val fileLogStorage = mock<FileLogStorage>()

    @Test
    fun `storage가 none일 때 콘솔에만 저장한다`() {
        val properties = LoggingProperties(enabled = true, storage = StorageType.NONE)
        val compositeStorage = CompositeLogStorage(consoleLogStorage, fileLogStorage, properties)

        val entry = createTestLogEntry()
        compositeStorage.store(entry)

        verify(consoleLogStorage).store(entry)
        verify(fileLogStorage, never()).store(entry)
    }

    @Test
    fun `storage가 file일 때 콘솔과 파일 모두에 저장한다`() {
        val properties = LoggingProperties(enabled = true, storage = StorageType.FILE)
        val compositeStorage = CompositeLogStorage(consoleLogStorage, fileLogStorage, properties)

        val entry = createTestLogEntry()
        compositeStorage.store(entry)

        verify(consoleLogStorage).store(entry)
        verify(fileLogStorage).store(entry)
    }

    @Test
    fun `fileLogStorage가 null이어도 콘솔에는 저장한다`() {
        val properties = LoggingProperties(enabled = true, storage = StorageType.FILE)
        val compositeStorage = CompositeLogStorage(consoleLogStorage, null, properties)

        val entry = createTestLogEntry()
        compositeStorage.store(entry)

        verify(consoleLogStorage).store(entry)
    }

    private fun createTestLogEntry(): LogEntry =
        LogEntry.request(
            timestamp = Instant.now(),
            requestId = "test-request-id",
            method = "GET",
            path = "/api/test",
            queryParams = null,
            headers = null,
        )
}
