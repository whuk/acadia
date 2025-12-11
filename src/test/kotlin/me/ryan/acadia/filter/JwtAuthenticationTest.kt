package me.ryan.acadia.filter

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtAuthenticationTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

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
}
