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

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
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

        return if (counter.count.get() > properties.limit) {
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
