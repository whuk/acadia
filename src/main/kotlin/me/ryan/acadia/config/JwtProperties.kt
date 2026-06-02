package me.ryan.acadia.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
) {
    init {
        // SEC-3: fail fast on a missing/weak secret. HMAC-SHA256 requires >= 256 bits.
        require(secret.length >= MIN_SECRET_LENGTH) {
            "jwt.secret must be configured and at least $MIN_SECRET_LENGTH bytes (256 bits)"
        }
    }

    companion object {
        const val MIN_SECRET_LENGTH = 32
    }
}
