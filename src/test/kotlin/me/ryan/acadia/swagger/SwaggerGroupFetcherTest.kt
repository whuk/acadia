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
import org.springframework.web.client.RestClient

class SwaggerGroupFetcherTest {
    private lateinit var wireMockServer: WireMockServer
    private lateinit var swaggerGroupFetcher: SwaggerGroupFetcher

    @BeforeEach
    fun setup() {
        wireMockServer = WireMockServer(wireMockConfig().port(8091))
        wireMockServer.start()
        swaggerGroupFetcher = SwaggerGroupFetcher(RestClient.builder().build())
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
        val groups = swaggerGroupFetcher.fetchGroups(service)

        // Then
        assertThat(groups).containsExactlyInAnyOrder("admin", "public")
    }

    @Test
    fun `서비스 연결 실패 시 빈 목록을 반환한다`() {
        // Given - service URL points to non-existent server
        val service =
            ServiceConfig(
                name = "unavailable-service",
                path = "/api/unavailable/**",
                url = "http://localhost:9999",
            )

        // When
        val groups = swaggerGroupFetcher.fetchGroups(service)

        // Then
        assertThat(groups).isEmpty()
    }
}
