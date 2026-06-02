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
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.WebUtils
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
@Order(FilterOrders.REQUEST_LOGGING)
class RequestLoggingFilter(
    private val loggingProperties: LoggingProperties,
    private val logStorage: LogStorage,
) : OncePerRequestFilter() {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (loggingProperties.includeBody) {
            // Body is only available after it is read for downstream forwarding,
            // so log after routing.
            try {
                filterChain.doFilter(request, response)
            } finally {
                logRequest(request, response)
            }
        } else {
            // No body needed: log before routing (deterministic, no response-timing race).
            logRequest(request, response)
            filterChain.doFilter(request, response)
        }
    }

    private fun logRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val requestId =
            request.getHeader(GatewayHeaders.X_REQUEST_ID)
                ?: response.getHeader(GatewayHeaders.X_REQUEST_ID)

        val queryParams =
            if (loggingProperties.includeQueryParams && request.parameterMap.isNotEmpty()) {
                objectMapper.writeValueAsString(
                    request.parameterMap.mapValues { it.value.firstOrNull() ?: "" },
                )
            } else {
                null
            }

        val headers =
            if (loggingProperties.includeHeaders) {
                val headerMap = request.headerNames.asSequence().associateWith { request.getHeader(it) }
                objectMapper.writeValueAsString(SensitiveHeaders.mask(headerMap))
            } else {
                null
            }

        val body =
            if (loggingProperties.includeBody) {
                cachedRequestBody(request)
                    ?.let { SensitiveFieldMasker.mask(it) }
                    ?.truncateIfNeeded(loggingProperties.maxBodySize)
            } else {
                null
            }

        val logEntry =
            LogEntry.request(
                timestamp = Instant.now(),
                requestId = requestId,
                method = request.method,
                path = request.requestURI,
                queryParams = queryParams,
                headers = headers,
                body = body,
            )

        logStorage.store(logEntry)
    }

    private fun cachedRequestBody(request: HttpServletRequest): String? {
        val cached = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper::class.java) ?: return null
        val bytes = cached.contentAsByteArray
        return if (bytes.isEmpty()) null else String(bytes, StandardCharsets.UTF_8)
    }

    private fun String.truncateIfNeeded(maxSize: Int): String =
        if (length > maxSize) {
            substring(0, maxSize) + "...[TRUNCATED]"
        } else {
            this
        }
}
