package me.ryan.acadia.observability

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SpanIdPropagationTest {
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
    fun `SpanId가 백엔드로 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk

        // Verify X-B3-SpanId was forwarded to backend
        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-B3-SpanId", matching("[a-f0-9]{16}")),
        )
    }
}
