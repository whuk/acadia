package me.ryan.acadia.filter

import com.fasterxml.jackson.databind.ObjectMapper
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.config.LoggingProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
class RequestLoggingFilter(
    private val loggingProperties: LoggingProperties,
) : GlobalFilter,
    Ordered {
    private val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java)
    private val objectMapper = ObjectMapper()

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        val requestId =
            request.headers.getFirst(GatewayHeaders.X_REQUEST_ID)
                ?: exchange.response.headers.getFirst(GatewayHeaders.X_REQUEST_ID)

        val logData =
            buildMap {
                put("type", "REQUEST")
                put("timestamp", Instant.now().toString())
                put("requestId", requestId)
                put("method", request.method.name())
                put("path", request.path.value())
                if (loggingProperties.includeQueryParams && request.queryParams.isNotEmpty()) {
                    put("queryParams", request.queryParams.toSingleValueMap())
                }
                if (loggingProperties.includeHeaders) {
                    val filteredHeaders =
                        request.headers
                            .toSingleValueMap()
                            .filterKeys { !it.equals("Authorization", ignoreCase = true) }
                    put("headers", filteredHeaders)
                }
            }

        logger.info(objectMapper.writeValueAsString(logData))

        return chain.filter(exchange)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1
}
