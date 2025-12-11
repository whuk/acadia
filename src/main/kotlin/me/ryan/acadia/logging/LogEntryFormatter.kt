package me.ryan.acadia.logging

import com.fasterxml.jackson.databind.ObjectMapper
import me.ryan.acadia.logging.entity.RequestLogEntry

object LogEntryFormatter {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    fun RequestLogEntry.toLogMap(): Map<String, Any?> =
        buildMap {
            put("type", "REQUEST")
            put("timestamp", timestamp.toString())
            put("requestId", requestId)
            put("method", method)
            put("path", path)
            queryParams?.let { put("queryParams", it) }
            headers?.let { put("headers", it) }
        }

    fun RequestLogEntry.toJson(): String = objectMapper.writeValueAsString(toLogMap())
}
