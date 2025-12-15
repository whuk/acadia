package me.ryan.acadia.cors

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CorsPreflightTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `허용된 Origin의 preflight 요청이 성공한다`() {
        webTestClient
            .options()
            .uri("/api/users/1")
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .valueEquals("Access-Control-Allow-Origin", "https://example.com")
    }

    @Test
    fun `허용되지 않은 Origin은 CORS 오류를 반환한다`() {
        webTestClient
            .options()
            .uri("/api/users/1")
            .header("Origin", "https://malicious.com")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus()
            .isForbidden
            .expectHeader()
            .doesNotExist("Access-Control-Allow-Origin")
    }

    @Test
    fun `허용된 HTTP 메서드만 CORS 응답에 포함된다`() {
        webTestClient
            .options()
            .uri("/api/users/1")
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .valueEquals("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE")
    }

    @Test
    fun `credentials가 허용된다`() {
        webTestClient
            .options()
            .uri("/api/users/1")
            .header("Origin", "https://example.com")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .valueEquals("Access-Control-Allow-Credentials", "true")
    }
}
