package me.ryan.acadia.routing

import me.ryan.acadia.support.JwtTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UndefinedRouteTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `정의되지 않은 경로는 404를 반환한다`() {
        // Authenticated request to an undefined API path must yield 404 (not 401).
        webTestClient
            .get()
            .uri("/api/undefined/path")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
