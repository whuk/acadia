package me.ryan.acadia.observability

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.notMatching
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import me.ryan.acadia.support.JwtTestSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * B1 / rule section 5: a valid inbound X-B3-TraceId is preserved for trace continuity; an
 * invalid one is regenerated so malformed/spoofed values never reach downstream.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class B3TraceIdPropagationTest {
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
    fun `유효한 인입 TraceId는 보존되어 백엔드로 전달된다`() {
        wireMock.stubFor(get(urlPathMatching("/users/.*")).willReturn(ok()))

        val inboundTraceId = "abcdef0123456789abcdef0123456789"

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .header("X-B3-TraceId", inboundTraceId)
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-B3-TraceId", equalTo(inboundTraceId)),
        )
    }

    @Test
    fun `형식이 부적합한 인입 TraceId는 새 값으로 대체된다`() {
        wireMock.stubFor(get(urlPathMatching("/users/.*")).willReturn(ok()))

        val invalidTraceId = "not-a-valid-trace!"

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .header("X-B3-TraceId", invalidTraceId)
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-B3-TraceId", matching("[a-f0-9]{16,32}"))
                .withHeader("X-B3-TraceId", notMatching(".*not-a-valid-trace.*")),
        )
    }
}
