package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.config.RateLimitProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
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

    // Time source; overridable in tests for deterministic window/eviction assertions.
    internal var clock: () -> Long = System::currentTimeMillis

    companion object {
        const val HEADER_LIMIT = "X-RateLimit-Limit"
        const val HEADER_REMAINING = "X-RateLimit-Remaining"
        const val HEADER_RESET = "X-RateLimit-Reset"

        private const val SWEEP_INTERVAL_MS = 60_000L
        private const val HEADER_FORWARDED_FOR = "X-Forwarded-For"
    }

    fun reset() {
        requestCounts.clear()
    }

    // Number of IPs currently tracked; visible for testing eviction behavior.
    internal fun trackedIpCount(): Int = requestCounts.size

    // REL-1: evict expired windows on a schedule instead of scanning the whole map on every
    // request. Keeps memory bounded without imposing O(N) work on the hot request path.
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    fun sweep() {
        val now = clock()
        requestCounts.entries.removeIf { now - it.value.windowStart > properties.windowMs }
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

        val clientIp = resolveClientIp(request)
        val now = clock()

        // REL-1: no whole-map scan here; expired-window eviction is handled by the scheduled sweep().
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

    // PERF-2b: behind a trusted proxy, the real client IP is the leftmost X-Forwarded-For entry.
    // When the proxy is not trusted, X-Forwarded-For is ignored to prevent rate-limit identity spoofing.
    private fun resolveClientIp(request: HttpServletRequest): String {
        if (properties.trustForwardedFor) {
            val forwardedFor = request.getHeader(HEADER_FORWARDED_FOR)
            if (!forwardedFor.isNullOrBlank()) {
                return forwardedFor.substringBefore(',').trim()
            }
        }
        return request.remoteAddr ?: "unknown"
    }

    private data class RequestCounter(
        val windowStart: Long,
        val count: AtomicInteger,
    )
}
