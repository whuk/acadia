package me.ryan.acadia.config

import io.netty.channel.ConnectTimeoutException
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono

@Configuration
@Order(-2)
class GlobalErrorHandler : WebExceptionHandler {
    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        if (isConnectTimeoutException(ex)) {
            exchange.response.statusCode = HttpStatus.GATEWAY_TIMEOUT
            return exchange.response.setComplete()
        }
        return Mono.error(ex)
    }

    private fun isConnectTimeoutException(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is ConnectTimeoutException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
