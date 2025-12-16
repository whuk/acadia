package me.ryan.acadia.swagger

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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

    private lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun setup() {
        wireMockServer = WireMockServer(wireMockConfig().port(8081))
        wireMockServer.start()
    }

    @AfterEach
    fun teardown() {
        wireMockServer.stop()
    }

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

    @Test
    fun `등록된 서비스의 api-docs가 Gateway를 통해 프록시된다`() {
        val userServiceApiDocs =
            """
            {
                "openapi": "3.0.1",
                "info": {
                    "title": "User Service API",
                    "version": "1.0.0"
                },
                "paths": {}
            }
            """.trimIndent()

        wireMockServer.stubFor(
            get(urlEqualTo("/v3/api-docs"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userServiceApiDocs),
                ),
        )

        webTestClient
            .get()
            .uri("/v3/api-docs/user-service")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("application/json")
            .expectBody()
            .jsonPath("$.info.title")
            .isEqualTo("User Service API")
    }

    @Test
    fun `Swagger UI 드롭다운에서 각 서비스를 선택할 수 있다`() {
        webTestClient
            .get()
            .uri("/v3/api-docs/swagger-config")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("application/json")
            .expectBody()
            .jsonPath("$.urls")
            .isArray
            .jsonPath("$.urls[?(@.name == 'user-service')]")
            .exists()
            .jsonPath("$.urls[?(@.name == 'order-service')]")
            .exists()
    }
}

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "gateway.services[0].name=enabled-service",
        "gateway.services[0].path=/api/enabled/**",
        "gateway.services[0].url=http://localhost:8081",
        "gateway.services[0].swagger-enabled=true",
        "gateway.services[1].name=disabled-service",
        "gateway.services[1].path=/api/disabled/**",
        "gateway.services[1].url=http://localhost:8082",
        "gateway.services[1].swagger-enabled=false",
    ],
)
@AutoConfigureWebTestClient
class SwaggerDisabledServiceTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `swagger-enabled false인 서비스는 목록에서 제외된다`() {
        webTestClient
            .get()
            .uri("/v3/api-docs/swagger-config")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.urls[?(@.name == 'enabled-service')]")
            .exists()
            .jsonPath("$.urls[?(@.name == 'disabled-service')]")
            .doesNotExist()
    }
}

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "gateway.services[0].name=service-alpha",
        "gateway.services[0].path=/api/alpha/**",
        "gateway.services[0].url=http://localhost:8081",
        "gateway.services[1].name=service-beta",
        "gateway.services[1].path=/api/beta/**",
        "gateway.services[1].url=http://localhost:8082",
        "gateway.services[2].name=service-gamma",
        "gateway.services[2].path=/api/gamma/**",
        "gateway.services[2].url=http://localhost:8083",
    ],
)
@AutoConfigureWebTestClient
class SwaggerDynamicUrlsTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `swagger-ui urls가 gateway services 기반으로 동적 생성된다`() {
        webTestClient
            .get()
            .uri("/v3/api-docs/swagger-config")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.urls[?(@.name == 'service-alpha')]")
            .exists()
            .jsonPath("$.urls[?(@.name == 'service-alpha')].url")
            .isEqualTo("/v3/api-docs/service-alpha")
            .jsonPath("$.urls[?(@.name == 'service-beta')]")
            .exists()
            .jsonPath("$.urls[?(@.name == 'service-beta')].url")
            .isEqualTo("/v3/api-docs/service-beta")
            .jsonPath("$.urls[?(@.name == 'service-gamma')]")
            .exists()
            .jsonPath("$.urls[?(@.name == 'service-gamma')].url")
            .isEqualTo("/v3/api-docs/service-gamma")
    }
}
