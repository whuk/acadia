package me.ryan.acadia.logging

import com.fasterxml.jackson.databind.ObjectMapper
import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.config.LoggingProperties.FileProperties
import me.ryan.acadia.logging.entity.LogEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class FileLogStorageTest {
    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private fun storageWriting(to: Path): FileLogStorage = FileLogStorage(LoggingProperties(file = FileProperties(path = to.toString())))

    private fun requestEntry(requestId: String): LogEntry =
        LogEntry.request(
            timestamp = Instant.parse("2026-06-02T00:00:00Z"),
            requestId = requestId,
            method = "GET",
            path = "/api/users/$requestId",
        )

    @Test
    fun `부모 디렉토리를 만들고 각 엔트리를 JSON 라인으로 추가한다`() {
        val logPath = tempDir.resolve("nested/logs/gateway.log")
        val storage = storageWriting(logPath)

        storage.store(requestEntry("a"))
        storage.store(requestEntry("b"))

        val lines = Files.readAllLines(logPath)
        assertThat(lines).hasSize(2)
        // Each line is valid JSON carrying the expected requestId.
        assertThat(objectMapper.readTree(lines[0]).get("requestId").asText()).isEqualTo("a")
        assertThat(objectMapper.readTree(lines[1]).get("requestId").asText()).isEqualTo("b")
    }

    @Test
    fun `동시 store 호출 시 모든 라인이 유실·깨짐 없이 기록된다`() {
        val logPath = tempDir.resolve("logs/concurrent.log")
        val storage = storageWriting(logPath)

        val threads = 16
        val perThread = 50
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) { t ->
            pool.submit {
                start.await()
                repeat(perThread) { i -> storage.store(requestEntry("$t-$i")) }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        val lines = Files.readAllLines(logPath)
        assertThat(lines).hasSize(threads * perThread)
        // Every line must be intact, parseable JSON (no interleaved/torn writes).
        lines.forEach { line -> assertThat(objectMapper.readTree(line).get("requestId")).isNotNull() }
    }

    @Test
    fun `destroy 후 다시 쓰면 핸들이 재오픈되어 기록이 이어진다`() {
        val logPath = tempDir.resolve("logs/reopen.log")
        val storage = storageWriting(logPath)

        storage.store(requestEntry("before"))
        storage.destroy() // releases the file handle

        storage.store(requestEntry("after")) // lazily reopens, appends

        val lines = Files.readAllLines(logPath)
        assertThat(lines).hasSize(2)
        assertThat(objectMapper.readTree(lines[1]).get("requestId").asText()).isEqualTo("after")
    }
}
