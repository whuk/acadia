package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = false,
    val limit: Int = 10,
    val burst: Int = 20,
    val windowMs: Long = 1000L,
    // PERF-2b: only honor X-Forwarded-For when the gateway sits behind a trusted proxy that sets it.
    // Off by default so an untrusted client cannot spoof its rate-limit identity.
    val trustForwardedFor: Boolean = false,
)
