package me.ryan.acadia.swagger

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import me.ryan.acadia.config.GatewayProperties.ServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class SwaggerGroupFetcherTest {
    private lateinit var wireMockServer: WireMockServer
    private lateinit var swaggerGroupFetcher: SwaggerGroupFetcher

    @BeforeEach
    fun setup() {
        wireMockServer = WireMockServer(wireMockConfig().port(8091))
        wireMockServer.start()
        swaggerGroupFetcher = SwaggerGroupFetcher(WebClient.builder().build())
    }

    @AfterEach
    fun teardown() {
        wireMockServer.stop()
    }

    @Test
    fun `서비스의 swagger-config에서 그룹 목록을 가져온다`() {
        // Given
        val swaggerConfigResponse =
            """
            {
                "configUrl": "/v3/api-docs/swagger-config",
                "urls": [
                    {"name": "admin", "url": "/v3/api-docs/admin"},
                    {"name": "public", "url": "/v3/api-docs/public"}
                ]
            }
            """.trimIndent()

        wireMockServer.stubFor(
            get(urlEqualTo("/v3/api-docs/swagger-config"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(swaggerConfigResponse),
                ),
        )

        val service =
            ServiceConfig(
                name = "user-service",
                path = "/api/users/**",
                url = "http://localhost:8091",
            )

        // When
        val groups = swaggerGroupFetcher.fetchGroups(service).block()

        // Then
        assertThat(groups).containsExactlyInAnyOrder("admin", "public")
    }
}
