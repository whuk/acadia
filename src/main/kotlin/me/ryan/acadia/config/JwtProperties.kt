package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "default-secret-key-for-testing-purposes-only-32bytes",
)
