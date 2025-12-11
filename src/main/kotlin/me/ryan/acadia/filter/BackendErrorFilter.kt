package me.ryan.acadia.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class BackendErrorFilter :
    GlobalFilter,
    Ordered {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        exchange.response.beforeCommit {
            val statusCode = exchange.response.statusCode
            // Convert backend 5xx errors to 502, but preserve:
            // - 502 (already a gateway error)
            // - 503 (Circuit Breaker open state)
            // - 504 (Gateway Timeout)
            if (statusCode != null &&
                statusCode.is5xxServerError &&
                statusCode != HttpStatus.BAD_GATEWAY &&
                statusCode != HttpStatus.SERVICE_UNAVAILABLE &&
                statusCode != HttpStatus.GATEWAY_TIMEOUT
            ) {
                exchange.response.setStatusCode(HttpStatus.BAD_GATEWAY)
            }
            Mono.empty()
        }
        return chain.filter(exchange)
    }

    override fun getOrder(): Int = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1
}
