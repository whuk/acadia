package me.ryan.acadia.logging.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "request_logs",
    indexes = [
        Index(name = "idx_request_logs_timestamp", columnList = "timestamp"),
        Index(name = "idx_request_logs_request_id", columnList = "request_id"),
    ],
)
class RequestLogEntry private constructor(
    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant,
    @Column(name = "request_id", length = 64)
    val requestId: String?,
    @Column(name = "method", nullable = false, length = 10)
    val method: String,
    @Column(name = "path", nullable = false, length = 2048)
    val path: String,
    @Column(name = "query_params", columnDefinition = "TEXT")
    val queryParams: String? = null,
    @Column(name = "headers", columnDefinition = "TEXT")
    val headers: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    companion object {
        fun create(
            timestamp: Instant,
            requestId: String?,
            method: String,
            path: String,
            queryParams: String? = null,
            headers: String? = null,
        ): RequestLogEntry =
            RequestLogEntry(
                timestamp = timestamp,
                requestId = requestId,
                method = method,
                path = path,
                queryParams = queryParams,
                headers = headers,
            )
    }
}
