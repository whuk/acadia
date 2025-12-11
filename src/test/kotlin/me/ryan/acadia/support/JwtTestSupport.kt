package me.ryan.acadia.support

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date

object JwtTestSupport {
    private const val SECRET = "default-secret-key-for-testing-purposes-only-32bytes"
    private val secretKey = Keys.hmacShaKeyFor(SECRET.toByteArray())

    fun generateValidToken(
        subject: String = "test-user",
        expirationMs: Long = 3600000,
    ): String =
        Jwts
            .builder()
            .subject(subject)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(secretKey)
            .compact()

    fun validAuthHeader(): String = "Bearer ${generateValidToken()}"
}
