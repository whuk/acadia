package me.ryan.acadia.filter

import me.ryan.acadia.common.GatewayHeaders
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class TraceIdFilter :
    GlobalFilter,
    Ordered {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val traceId = generateTraceId()

        val mutatedRequest =
            exchange.request
                .mutate()
                .header(GatewayHeaders.X_B3_TRACE_ID, traceId)
                .build()

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
    }

    private fun generateTraceId(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 16)

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1
}
