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
class SpanIdFilter :
    GlobalFilter,
    Ordered {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val spanId = generateSpanId()

        val mutatedRequest =
            exchange.request
                .mutate()
                .header(GatewayHeaders.X_B3_SPAN_ID, spanId)
                .build()

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
    }

    private fun generateSpanId(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 16)

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 2
}
