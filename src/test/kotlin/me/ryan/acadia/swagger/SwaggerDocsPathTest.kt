package me.ryan.acadia.swagger

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 발견 3: a non-springdoc backend (e.g. FastAPI serving OpenAPI at /openapi.json) must be
 * aggregatable by configuring a per-service downstream docs path.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "gateway.services[0].name=fastapi-service",
        "gateway.services[0].path=/api/fastapi/**",
        "gateway.services[0].url=http://localhost:8094",
        "gateway.services[0].docs-path=/openapi.json",
    ],
)
@AutoConfigureWebTestClient
class SwaggerDocsPathTest {
    companion object {
        private val wireMockServer = WireMockServer(wireMockConfig().port(8094))

        @JvmStatic
        @BeforeAll
        fun setup() {
            wireMockServer.start()
            val openapi =
                """
                {"openapi": "3.1.0", "info": {"title": "FastAPI Service", "version": "0.1.0"}, "paths": {}}
                """.trimIndent()
            wireMockServer.stubFor(
                get(urlEqualTo("/openapi.json"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(openapi),
                    ),
            )
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            wireMockServer.stop()
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `docs-path가 설정되면 서비스 문서 요청이 백엔드의 해당 경로로 라우팅된다`() {
        webTestClient
            .get()
            .uri("/v3/api-docs/fastapi-service")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.info.title")
            .isEqualTo("FastAPI Service")
    }
}
