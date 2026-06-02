package me.ryan.acadia.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import me.ryan.acadia.config.GatewayProperties.ServiceConfig
import me.ryan.acadia.swagger.SwaggerGroupFetcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestClientConfigTest {
    private lateinit var wireMockServer: WireMockServer

    @BeforeEach
    fun setup() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
    }

    @AfterEach
    fun teardown() {
        wireMockServer.stop()
    }

    @Test
    fun `백엔드 응답이 지연되면 read 타임아웃으로 빈 목록을 반환한다`() {
        // Given: a backend that stalls far longer than the configured read timeout
        wireMockServer.stubFor(
            get(urlEqualTo("/v3/api-docs/swagger-config"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5000)
                        .withBody("""{"urls":[{"name":"admin","url":"/v3/api-docs/admin"}]}"""),
                ),
        )

        // RestClient built from the production config carries bounded timeouts.
        val fetcher = SwaggerGroupFetcher(RestClientConfig().restClient())
        val service =
            ServiceConfig(
                name = "user-service",
                path = "/api/users/**",
                url = "http://localhost:${wireMockServer.port()}",
            )

        // When / Then: the read timeout fires before the 5s delay, so fetch fails fast and falls back to empty.
        assertThat(fetcher.fetchGroups(service)).isEmpty()
    }
}
