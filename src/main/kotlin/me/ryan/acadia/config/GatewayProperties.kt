package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    val services: List<ServiceConfig> = emptyList(),
    val retry: RetryConfig = RetryConfig(),
) {
    data class ServiceConfig(
        val name: String = "",
        val path: String = "",
        val url: String = "",
        val stripPrefix: Int = 1,
        val hasPublicPath: Boolean = false,
        val swaggerEnabled: Boolean = true,
        val swaggerGroups: List<String> = emptyList(),
    )

    data class RetryConfig(
        val retries: Int = 3,
        val statuses: List<HttpStatus> = listOf(HttpStatus.INTERNAL_SERVER_ERROR),
        val methods: List<HttpMethod> = listOf(HttpMethod.GET),
    )
}
