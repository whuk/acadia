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
}
