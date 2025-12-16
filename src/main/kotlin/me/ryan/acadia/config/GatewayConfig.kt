package me.ryan.acadia.config

import me.ryan.acadia.common.GatewayPaths
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

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
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
        var routes = builder.routes()

        props.services.forEach { service ->
            // Swagger API docs proxy route
            routes =
                routes.route("$SWAGGER_ROUTE_PREFIX${service.name}") { r ->
                    r
                        .path("${GatewayPaths.SWAGGER_DOCS}/${service.name}")
                        .filters { f ->
                            f.setPath(GatewayPaths.SWAGGER_DOCS)
                        }.uri(service.url)
                }

            if (service.hasPublicPath) {
                val publicPath = service.path.replace(GatewayPaths.API_PREFIX, GatewayPaths.PUBLIC_PREFIX)
                routes =
                    routes.route("$PUBLIC_ROUTE_PREFIX${service.name}") { r ->
                        r
                            .path(publicPath)
                            .filters { f -> f.applyCommonFilters(service.stripPrefix + 1) }
                            .uri(service.url)
                    }
            }

            routes =
                routes.route(service.name) { r ->
                    r
                        .path(service.path)
                        .filters { f -> f.applyCommonFilters(service.stripPrefix) }
                        .uri(service.url)
                }
        }

        return routes.build()
    }

    private fun GatewayFilterSpec.applyCommonFilters(stripPrefix: Int): GatewayFilterSpec =
        this
            .stripPrefix(stripPrefix)
            .preserveHostHeader()
            .circuitBreaker { config ->
                config.setName(CIRCUIT_BREAKER_NAME)
                config.addStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.name)
                config.addStatusCode(HttpStatus.BAD_GATEWAY.name)
            }.retry { config ->
                config.setRetries(props.retry.retries)
                config.setStatuses(*props.retry.statuses.toTypedArray())
                config.setMethods(*props.retry.methods.toTypedArray())
                config.setExceptions(*emptyArray<Class<out Throwable>>())
            }
}
