package me.ryan.acadia.filter

import me.ryan.acadia.config.LoggingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

const val CACHED_REQUEST_BODY_ATTR = "gatewayLoggingRequestBody"

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
class CachedBodyGatewayFilter(
    private val loggingProperties: LoggingProperties,
) : GlobalFilter,
    Ordered {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        if (!loggingProperties.includeBody) {
            return chain.filter(exchange)
        }

        // Skip body caching for multipart/form-data requests
        val contentType = exchange.request.headers.contentType
        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            return chain.filter(exchange)
        }

        return DataBufferUtils
            .join(exchange.request.body)
            .defaultIfEmpty(exchange.response.bufferFactory().wrap(ByteArray(0)))
            .flatMap { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                DataBufferUtils.release(dataBuffer)

                val bodyString = String(bytes, StandardCharsets.UTF_8)

                // Store the cached body for logging (as String)
                exchange.attributes[CACHED_REQUEST_BODY_ATTR] = bodyString

                // Create a decorated request that returns the cached body
                val decoratedRequest = createDecoratedRequest(exchange.request, bytes, exchange)
                val mutatedExchange = exchange.mutate().request(decoratedRequest).build()

                chain.filter(mutatedExchange)
            }
    }

    private fun createDecoratedRequest(
        request: ServerHttpRequest,
        cachedBody: ByteArray,
        exchange: ServerWebExchange,
    ): ServerHttpRequestDecorator =
        object : ServerHttpRequestDecorator(request) {
            override fun getBody(): Flux<DataBuffer> {
                if (cachedBody.isEmpty()) {
                    return Flux.empty()
                }
                val buffer = exchange.response.bufferFactory().wrap(cachedBody)
                return Flux.just(buffer)
            }
        }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
