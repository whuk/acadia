package me.ryan.acadia.logging

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "request_logs")
data class RequestLogEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val timestamp: Instant,
    val requestId: String?,
    val method: String,
    val path: String,
    val queryParams: String? = null,
    val headers: String? = null,
)
