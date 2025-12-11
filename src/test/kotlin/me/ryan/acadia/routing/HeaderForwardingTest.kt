package me.ryan.acadia.routing

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HeaderForwardingTest {
    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension = WireMockExtension.newInstance().build()

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("user-service.url") { wireMock.baseUrl() }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `커스텀 헤더가 라우팅 시 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .header("X-Custom-Header", "custom-value")
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-Custom-Header", equalTo("custom-value")),
        )
    }

    @Test
    fun `Accept 헤더가 라우팅 시 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .header("Accept", "application/json")
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("Accept", containing("application/json")),
        )
    }

    @Test
    fun `Content-Type 헤더가 라우팅 시 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("Content-Type", equalTo("application/json")),
        )
    }

    @Test
    fun `Authorization 헤더가 라우팅 시 전달된다`() {
        val validToken = JwtTestSupport.validAuthHeader()

        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", validToken)
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("Authorization", equalTo(validToken)),
        )
    }

    @Test
    fun `여러 헤더가 동시에 라우팅 시 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .header("X-Request-Id", "req-123")
            .header("X-Correlation-Id", "corr-456")
            .header("Accept-Language", "ko-KR")
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-Request-Id", equalTo("req-123"))
                .withHeader("X-Correlation-Id", equalTo("corr-456"))
                .withHeader("Accept-Language", equalTo("ko-KR")),
        )
    }
}
