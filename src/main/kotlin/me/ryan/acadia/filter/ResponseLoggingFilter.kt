package me.ryan.acadia.filter

import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.logging.LogStorage
import me.ryan.acadia.logging.entity.LogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
class ResponseLoggingFilter(
    private val logStorage: LogStorage,
) : GlobalFilter,
    Ordered {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val startTime = Instant.now()

        return chain
            .filter(exchange)
            .then(
                Mono.fromRunnable {
                    val endTime = Instant.now()
                    val duration = endTime.toEpochMilli() - startTime.toEpochMilli()
                    val requestId = exchange.response.headers.getFirst(GatewayHeaders.X_REQUEST_ID)

                    val logEntry =
                        LogEntry.response(
                            timestamp = endTime,
                            requestId = requestId,
                            statusCode = exchange.response.statusCode?.value() ?: 0,
                            duration = duration,
                        )

                    logStorage.store(logEntry)
                },
            )
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
