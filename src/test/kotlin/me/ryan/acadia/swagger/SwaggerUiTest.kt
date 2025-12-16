package me.ryan.acadia.swagger

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SwaggerUiTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `swagger-ui html 엔드포인트가 Swagger UI를 반환한다`() {
        webTestClient
            .get()
            .uri("/swagger-ui.html")
            .exchange()
            .expectStatus()
            .is3xxRedirection
            .expectHeader()
            .location("/swagger-ui/index.html")
    }

    @Test
    fun `v3 api-docs 엔드포인트가 Gateway의 OpenAPI 스펙을 반환한다`() {
        webTestClient
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("application/json")
            .expectBody()
            .jsonPath("$.openapi")
            .exists()
            .jsonPath("$.info.title")
            .exists()
    }
}
