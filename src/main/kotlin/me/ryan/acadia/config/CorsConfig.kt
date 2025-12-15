package me.ryan.acadia.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(
    private val corsProperties: CorsProperties,
) {
    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = corsProperties.allowedMethods
                allowedHeaders = corsProperties.allowedHeaders
                allowCredentials = corsProperties.allowCredentials
                maxAge = corsProperties.maxAge
            }

        val source =
            UrlBasedCorsConfigurationSource().apply {
                registerCorsConfiguration("/api/**", configuration)
            }

        return CorsWebFilter(source)
    }
}
