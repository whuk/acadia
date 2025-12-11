package me.ryan.acadia.resilience

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.Scenario
import me.ryan.acadia.support.JwtTestSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.HttpHeaders
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
class RetryTest {
    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension = WireMockExtension.newInstance().build()

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.services[0].name") { "user-service" }
            registry.add("gateway.services[0].path") { "/api/users/**" }
            registry.add("gateway.services[0].url") { wireMock.baseUrl() }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `백엔드 실패 시 최대 3회 재시도한다`() {
        // First request: 500 error
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serverError())
                .willSetStateTo("first-failure"),
        )

        // Second request: 500 error
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .inScenario("retry")
                .whenScenarioStateIs("first-failure")
                .willReturn(serverError())
                .willSetStateTo("second-failure"),
        )

        // Third request: success
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .inScenario("retry")
                .whenScenarioStateIs("second-failure")
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk

        // Verify 3 requests were made (initial + 2 retries)
        wireMock.verify(3, getRequestedFor(urlPathMatching("/users/.*")))
    }
}
