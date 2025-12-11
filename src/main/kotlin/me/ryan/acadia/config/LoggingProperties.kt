package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway.logging")
data class LoggingProperties(
    val enabled: Boolean = false,
    val includeHeaders: Boolean = false,
    val includeQueryParams: Boolean = true,
)
