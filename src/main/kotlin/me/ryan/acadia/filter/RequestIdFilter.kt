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
class RequestIdFilter :
    GlobalFilter,
    Ordered {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val existingRequestId = exchange.request.headers.getFirst(GatewayHeaders.X_REQUEST_ID)
        val requestId = existingRequestId ?: UUID.randomUUID().toString()

        val mutatedExchange =
            if (existingRequestId == null) {
                // Add X-Request-Id to request headers for backend only if not present
                val mutatedRequest =
                    exchange.request
                        .mutate()
                        .header(GatewayHeaders.X_REQUEST_ID, requestId)
                        .build()
                exchange.mutate().request(mutatedRequest).build()
            } else {
                exchange
            }

        // Add X-Request-Id to response headers for client
        exchange.response.headers.add(GatewayHeaders.X_REQUEST_ID, requestId)

        return chain.filter(mutatedExchange)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
