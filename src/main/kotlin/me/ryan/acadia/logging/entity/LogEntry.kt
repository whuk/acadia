package me.ryan.acadia.logging.entity

import java.time.Instant

/**
 * Immutable log record for console/file storage. No persistence mapping:
 * gateway traffic logs are written to console or file, not a database.
 */
class LogEntry private constructor(
    val type: LogType,
    val timestamp: Instant,
    val requestId: String?,
    val method: String? = null,
    val path: String? = null,
    val queryParams: String? = null,
    val headers: String? = null,
    val body: String? = null,
    val statusCode: Int? = null,
    val duration: Long? = null,
) {
    companion object {
        fun request(
            timestamp: Instant,
            requestId: String?,
            method: String,
            path: String,
            queryParams: String? = null,
            headers: String? = null,
            body: String? = null,
        ): LogEntry =
            LogEntry(
                type = LogType.REQUEST,
                timestamp = timestamp,
                requestId = requestId,
                method = method,
                path = path,
                queryParams = queryParams,
                headers = headers,
                body = body,
            )

        fun response(
            timestamp: Instant,
            requestId: String?,
            statusCode: Int,
            duration: Long,
            headers: String? = null,
            body: String? = null,
        ): LogEntry =
            LogEntry(
                type = LogType.RESPONSE,
                timestamp = timestamp,
                requestId = requestId,
                statusCode = statusCode,
                duration = duration,
                headers = headers,
                body = body,
            )
    }
}
