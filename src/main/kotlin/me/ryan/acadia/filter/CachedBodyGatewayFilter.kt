package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.config.LoggingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
@Order(FilterOrders.CACHED_BODY)
class CachedBodyGatewayFilter(
    private val loggingProperties: LoggingProperties,
) : OncePerRequestFilter() {
    companion object {
        // Cache memory cap (independent of the log display limit `maxBodySize`),
        // so bodies larger than the log limit are still fully captured then truncated for logging.
        private const val MAX_CACHE_BYTES = 1024 * 1024
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!loggingProperties.includeBody) {
            filterChain.doFilter(request, response)
            return
        }

        // Skip body caching for multipart/form-data requests
        val contentType = request.contentType
        if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            filterChain.doFilter(request, response)
            return
        }

        // Wrap so the request body is cached as it is read for downstream forwarding;
        // RequestLoggingFilter (inner) reads the cached bytes after routing.
        filterChain.doFilter(ContentCachingRequestWrapper(request, MAX_CACHE_BYTES), response)
    }
}
