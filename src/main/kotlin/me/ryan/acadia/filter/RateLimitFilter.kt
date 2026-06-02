package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.config.RateLimitProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
@Order(FilterOrders.RATE_LIMIT)
class RateLimitFilter(
    private val properties: RateLimitProperties,
) : OncePerRequestFilter() {
    private val requestCounts = ConcurrentHashMap<String, RequestCounter>()

    companion object {
        const val HEADER_LIMIT = "X-RateLimit-Limit"
        const val HEADER_REMAINING = "X-RateLimit-Remaining"
        const val HEADER_RESET = "X-RateLimit-Reset"
    }

    fun reset() {
        requestCounts.clear()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val clientIp = request.remoteAddr ?: "unknown"
        val now = System.currentTimeMillis()

        // REL-1: evict expired windows so the map does not grow unbounded.
        requestCounts.entries.removeIf { now - it.value.windowStart > properties.windowMs }

        val counter =
            requestCounts.compute(clientIp) { _, existing ->
                if (existing == null || now - existing.windowStart > properties.windowMs) {
                    RequestCounter(now, AtomicInteger(1))
                } else {
                    existing.count.incrementAndGet()
                    existing
                }
            }!!

        val currentCount = counter.count.get()
        val remaining = (properties.burst - currentCount).coerceAtLeast(0)
        val resetTime = (counter.windowStart + properties.windowMs) / 1000

        response.addHeader(HEADER_LIMIT, properties.burst.toString())
        response.addHeader(HEADER_REMAINING, remaining.toString())
        response.addHeader(HEADER_RESET, resetTime.toString())

        if (currentCount > properties.burst) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            return
        }

        filterChain.doFilter(request, response)
    }

    private data class RequestCounter(
        val windowStart: Long,
        val count: AtomicInteger,
    )
}
