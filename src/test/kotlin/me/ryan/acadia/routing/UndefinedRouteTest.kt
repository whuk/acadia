package me.ryan.acadia.routing

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UndefinedRouteTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `정의되지 않은 경로는 404를 반환한다`() {
        webTestClient
            .get()
            .uri("/api/undefined/path")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
