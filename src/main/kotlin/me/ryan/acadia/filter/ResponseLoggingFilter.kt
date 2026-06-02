package me.ryan.acadia.filter

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.logging.LogStorage
import me.ryan.acadia.logging.SensitiveFieldMasker
import me.ryan.acadia.logging.SensitiveHeaders
import me.ryan.acadia.logging.entity.LogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
@Order(FilterOrders.RESPONSE_LOGGING)
class ResponseLoggingFilter(
    private val logStorage: LogStorage,
    private val loggingProperties: LoggingProperties,
) : OncePerRequestFilter() {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startTime = Instant.now()

        if (!loggingProperties.includeBody) {
            try {
                filterChain.doFilter(request, response)
            } finally {
                logResponse(response, startTime, null)
            }
            return
        }

        val wrapped = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(request, wrapped)
        } finally {
            val body = String(wrapped.contentAsByteArray, StandardCharsets.UTF_8)
            logResponse(wrapped, startTime, body.ifEmpty { null })
            wrapped.copyBodyToResponse()
        }
    }

    private fun logResponse(
        response: HttpServletResponse,
        startTime: Instant,
        body: String?,
    ) {
        val endTime = Instant.now()
        val duration = endTime.toEpochMilli() - startTime.toEpochMilli()
        val requestId = response.getHeader(GatewayHeaders.X_REQUEST_ID)

        val truncatedBody =
            body
                ?.let { SensitiveFieldMasker.mask(it) }
                ?.truncateIfNeeded(loggingProperties.maxBodySize)

        val headers =
            if (loggingProperties.includeHeaders) {
                val headerMap = response.headerNames.associateWith { response.getHeader(it) }
                objectMapper.writeValueAsString(SensitiveHeaders.mask(headerMap))
            } else {
                null
            }

        val logEntry =
            LogEntry.response(
                timestamp = endTime,
                requestId = requestId,
                statusCode = response.status,
                duration = duration,
                headers = headers,
                body = truncatedBody,
            )

        logStorage.store(logEntry)
    }

    private fun String.truncateIfNeeded(maxSize: Int): String =
        if (length > maxSize) {
            substring(0, maxSize) + "...[TRUNCATED]"
        } else {
            this
        }
}
