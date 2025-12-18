package me.ryan.acadia.filter

import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.logging.LogStorage
import me.ryan.acadia.logging.SensitiveFieldMasker
import me.ryan.acadia.logging.entity.LogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
class ResponseLoggingFilter(
    private val logStorage: LogStorage,
    private val loggingProperties: LoggingProperties,
    private val modifyResponseBodyFilterFactory: ModifyResponseBodyGatewayFilterFactory,
) : GlobalFilter,
    Ordered {
    companion object {
        const val RESPONSE_BODY_ATTR = "cachedResponseBody"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val startTime = Instant.now()

        if (!loggingProperties.includeBody) {
            return chain
                .filter(exchange)
                .then(
                    Mono.fromRunnable {
                        logResponse(exchange, startTime, null)
                    },
                )
        }

        // Use ModifyResponseBodyGatewayFilterFactory to capture the body
        val config =
            ModifyResponseBodyGatewayFilterFactory.Config().apply {
                setInClass(String::class.java)
                setOutClass(String::class.java)
                setRewriteFunction(String::class.java, String::class.java) { ex, body ->
                    logResponse(ex, startTime, body)
                    Mono.justOrEmpty(body)
                }
            }

        val modifyBodyFilter = modifyResponseBodyFilterFactory.apply(config)

        return modifyBodyFilter.filter(exchange, chain)
    }

    private fun logResponse(
        exchange: ServerWebExchange,
        startTime: Instant,
        body: String?,
    ) {
        val endTime = Instant.now()
        val duration = endTime.toEpochMilli() - startTime.toEpochMilli()
        val requestId = exchange.response.headers.getFirst(GatewayHeaders.X_REQUEST_ID)

        val truncatedBody =
            body
                ?.let { SensitiveFieldMasker.mask(it) }
                ?.truncateIfNeeded(loggingProperties.maxBodySize)

        val logEntry =
            LogEntry.response(
                timestamp = endTime,
                requestId = requestId,
                statusCode = exchange.response.statusCode?.value() ?: 0,
                duration = duration,
                body = truncatedBody,
            )

        logStorage.store(logEntry)
    }

    private fun String.truncateIfNeeded(maxSize: Int): String =
        if (length > maxSize) {
            substring(0, maxSize) + "...[TRUNCATED]"
        } else {
            this
        }

    override fun getOrder(): Int = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1
}
