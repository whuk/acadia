package me.ryan.acadia.logging.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "gateway_logs",
    indexes = [
        Index(name = "idx_gateway_logs_timestamp", columnList = "timestamp"),
        Index(name = "idx_gateway_logs_request_id", columnList = "request_id"),
        Index(name = "idx_gateway_logs_type", columnList = "type"),
    ],
)
class LogEntry private constructor(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    val type: LogType,
    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant,
    @Column(name = "request_id", length = 64)
    val requestId: String?,
    @Column(name = "method", length = 10)
    val method: String? = null,
    @Column(name = "path", length = 2048)
    val path: String? = null,
    @Column(name = "query_params", columnDefinition = "TEXT")
    val queryParams: String? = null,
    @Column(name = "headers", columnDefinition = "TEXT")
    val headers: String? = null,
    @Column(name = "status_code")
    val statusCode: Int? = null,
    @Column(name = "duration")
    val duration: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    companion object {
        fun request(
            timestamp: Instant,
            requestId: String?,
            method: String,
            path: String,
            queryParams: String? = null,
            headers: String? = null,
        ): LogEntry =
            LogEntry(
                type = LogType.REQUEST,
                timestamp = timestamp,
                requestId = requestId,
                method = method,
                path = path,
                queryParams = queryParams,
                headers = headers,
            )

        fun response(
            timestamp: Instant,
            requestId: String?,
            statusCode: Int,
            duration: Long,
        ): LogEntry =
            LogEntry(
                type = LogType.RESPONSE,
                timestamp = timestamp,
                requestId = requestId,
                statusCode = statusCode,
                duration = duration,
            )
    }
}
