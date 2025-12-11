package me.ryan.acadia.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import me.ryan.acadia.config.JwtProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.Date

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtAuthenticationTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var jwtProperties: JwtProperties

    @Test
    fun `Authorization 헤더 없는 요청은 401을 반환한다`() {
        webTestClient
            .get()
            .uri("/api/users/1")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `잘못된 JWT 토큰은 401을 반환한다`() {
        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", "Bearer invalid-token")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `만료된 JWT 토큰은 401을 반환한다`() {
        val expiredToken = createExpiredToken()

        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", "Bearer $expiredToken")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    private fun createExpiredToken(): String {
        val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
        val pastDate = Date(System.currentTimeMillis() - 3600000) // 1 hour ago

        return Jwts
            .builder()
            .subject("test-user")
            .expiration(pastDate)
            .signWith(secretKey)
            .compact()
    }
}
