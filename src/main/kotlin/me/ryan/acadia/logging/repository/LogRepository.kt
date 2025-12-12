package me.ryan.acadia.logging.repository

import me.ryan.acadia.logging.entity.LogEntry
import org.springframework.data.jpa.repository.JpaRepository

interface LogRepository : JpaRepository<LogEntry, Long> {
    fun findByRequestIdOrderByTimestamp(requestId: String): List<LogEntry>
}
