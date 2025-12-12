package me.ryan.acadia.logging

import com.fasterxml.jackson.databind.ObjectMapper
import me.ryan.acadia.logging.entity.LogEntry
import me.ryan.acadia.logging.entity.LogType

object LogEntryFormatter {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    fun LogEntry.toLogMap(): Map<String, Any?> =
        buildMap {
            put("type", type.name)
            put("timestamp", timestamp.toString())
            put("requestId", requestId)

            when (type) {
                LogType.REQUEST -> {
                    method?.let { put("method", it) }
                    path?.let { put("path", it) }
                    queryParams?.let { put("queryParams", it) }
                    headers?.let { put("headers", it) }
                }
                LogType.RESPONSE -> {
                    statusCode?.let { put("statusCode", it) }
                    duration?.let { put("duration", it) }
                }
            }
        }

    fun LogEntry.toJson(): String = objectMapper.writeValueAsString(toLogMap())
}
