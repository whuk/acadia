package me.ryan.acadia.config

import me.ryan.acadia.common.GatewayPaths
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker
import org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.function.RequestPredicates.path
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse
import java.util.Optional

@Configuration
@EnableConfigurationProperties(GatewayProperties::class)
class GatewayConfig(
    private val props: GatewayProperties,
) {
    companion object {
        const val CIRCUIT_BREAKER_NAME = "gatewayCircuitBreaker"
        private const val SWAGGER_ROUTE_PREFIX = "swagger-"
        private const val PUBLIC_ROUTE_PREFIX = "public-"
    }

    @Bean
    fun gatewayRoutes(): RouterFunction<ServerResponse> {
        val routes = mutableListOf<RouterFunction<ServerResponse>>()

        props.services.forEach { service ->
            // Swagger API docs proxy route for service/group format
            routes +=
                route("$SWAGGER_ROUTE_PREFIX${service.name}-group")
                    .route(path("${GatewayPaths.SWAGGER_DOCS}/${service.name}/{group}"), http())
                    .before(uri(service.url))
                    .before(
                        rewritePath(
                            "${GatewayPaths.SWAGGER_DOCS}/${service.name}/(?<group>.*)",
                            "${GatewayPaths.SWAGGER_DOCS}/\${group}",
                        ),
                    ).build()

            // Swagger API docs proxy route
            routes +=
                route("$SWAGGER_ROUTE_PREFIX${service.name}")
                    .route(path("${GatewayPaths.SWAGGER_DOCS}/${service.name}"), http())
                    .before(uri(service.url))
                    .before(setPath(GatewayPaths.SWAGGER_DOCS))
                    .build()

            // Public (unauthenticated) route
            if (service.hasPublicPath) {
                val publicPath = service.path.replace(GatewayPaths.API_PREFIX, GatewayPaths.PUBLIC_PREFIX)
                routes +=
                    route("$PUBLIC_ROUTE_PREFIX${service.name}")
                        .route(path(publicPath), http())
                        .before(uri(service.url))
                        .before(stripPrefix(service.stripPrefix + 1))
                        .filter(circuitBreaker { it.setId(CIRCUIT_BREAKER_NAME).setStatusCodes(SERVER_ERROR_CODE) })
                        .filter(
                            retry {
                                it
                                    .setRetries(props.retry.retries)
                                    .setSeries(setOf(HttpStatus.Series.SERVER_ERROR))
                                    .setMethods(props.retry.methods.toSet())
                            },
                        ).build()
            }

            // Main route
            routes +=
                route(service.name)
                    .route(path(service.path), http())
                    .before(uri(service.url))
                    .before(stripPrefix(service.stripPrefix))
                    .filter(circuitBreaker { it.setId(CIRCUIT_BREAKER_NAME).setStatusCodes(SERVER_ERROR_CODE) })
                    .filter(
                        retry {
                            it
                                .setRetries(props.retry.retries)
                                .setSeries(setOf(HttpStatus.Series.SERVER_ERROR))
                                .setMethods(props.retry.methods.toSet())
                        },
                    ).build()
        }

        // REL-3b: tolerate an empty service list instead of crashing startup on reduce().
        // The fallback is a RouterFunction that matches nothing.
        return routes.reduceOrNull { acc, r -> acc.and(r) }
            ?: RouterFunction<ServerResponse> { Optional.empty() }
    }
}

private const val SERVER_ERROR_CODE = "500"
