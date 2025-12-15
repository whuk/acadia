package me.ryan.acadia.filter

import me.ryan.acadia.config.RateLimitProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class RateLimitFilter(
    private val properties: RateLimitProperties,
) : WebFilter {
    private val requestCounts = ConcurrentHashMap<String, RequestCounter>()

    companion object {
        const val HEADER_LIMIT = "X-RateLimit-Limit"
        const val HEADER_REMAINING = "X-RateLimit-Remaining"
        const val HEADER_RESET = "X-RateLimit-Reset"
    }

    fun reset() {
        requestCounts.clear()
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        if (!properties.enabled) {
            return chain.filter(exchange)
        }

        val clientIp =
            exchange.request.remoteAddress
                ?.address
                ?.hostAddress ?: "unknown"
        val now = System.currentTimeMillis()

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

        exchange.response.headers.add(HEADER_LIMIT, properties.burst.toString())
        exchange.response.headers.add(HEADER_REMAINING, remaining.toString())
        exchange.response.headers.add(HEADER_RESET, resetTime.toString())

        return if (currentCount > properties.burst) {
            exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
            exchange.response.setComplete()
        } else {
            chain.filter(exchange)
        }
    }

    private data class RequestCounter(
        val windowStart: Long,
        val count: AtomicInteger,
    )
}
