package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway.rate-limit")
data class RateLimitProperties(
    val limit: Int = 10,
    val windowMs: Long = 1000L,
)
