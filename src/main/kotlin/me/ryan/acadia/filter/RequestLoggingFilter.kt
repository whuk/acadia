package me.ryan.acadia.filter

import com.fasterxml.jackson.databind.ObjectMapper
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.config.LoggingProperties
import me.ryan.acadia.logging.LogStorage
import me.ryan.acadia.logging.entity.RequestLogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "gateway.logging", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(LoggingProperties::class)
class RequestLoggingFilter(
    private val loggingProperties: LoggingProperties,
    private val logStorage: LogStorage,
) : GlobalFilter,
    Ordered {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        val requestId =
            request.headers.getFirst(GatewayHeaders.X_REQUEST_ID)
                ?: exchange.response.headers.getFirst(GatewayHeaders.X_REQUEST_ID)

        val queryParams =
            if (loggingProperties.includeQueryParams && request.queryParams.isNotEmpty()) {
                objectMapper.writeValueAsString(request.queryParams.toSingleValueMap())
            } else {
                null
            }

        val headers =
            if (loggingProperties.includeHeaders) {
                val filteredHeaders =
                    request.headers
                        .toSingleValueMap()
                        .filterKeys { !it.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true) }
                objectMapper.writeValueAsString(filteredHeaders)
            } else {
                null
            }

        val logEntry =
            RequestLogEntry.create(
                timestamp = Instant.now(),
                requestId = requestId,
                method = request.method.name(),
                path = request.path.value(),
                queryParams = queryParams,
                headers = headers,
            )

        logStorage.store(logEntry)

        return chain.filter(exchange)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1
}
