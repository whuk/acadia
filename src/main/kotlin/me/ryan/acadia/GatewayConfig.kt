package me.ryan.acadia

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GatewayConfig {

    @Value("\${user-service.url:http://localhost:8081}")
    private lateinit var userServiceUrl: String

    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
        return builder.routes()
            .route("user-service") { r ->
                r.path("/api/users/**")
                    .filters { f -> f.stripPrefix(1) }
                    .uri(userServiceUrl)
            }
            .build()
    }
}
