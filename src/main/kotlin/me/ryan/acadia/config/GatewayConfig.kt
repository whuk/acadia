package me.ryan.acadia.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GatewayConfig {
    @Value("\${user-service.url}")
    private lateinit var userServiceUrl: String

    @Value("\${order-service.url}")
    private lateinit var orderServiceUrl: String

    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator =
        builder
            .routes()
            .route("public-user-service") { r ->
                r
                    .path("/api/public/users/**")
                    .filters { f -> f.stripPrefix(2).preserveHostHeader() }
                    .uri(userServiceUrl)
            }.route("user-service") { r ->
                r
                    .path("/api/users/**")
                    .filters { f -> f.stripPrefix(1).preserveHostHeader() }
                    .uri(userServiceUrl)
            }.route("order-service") { r ->
                r
                    .path("/api/orders/**")
                    .filters { f -> f.stripPrefix(1).preserveHostHeader() }
                    .uri(orderServiceUrl)
            }.build()
}
