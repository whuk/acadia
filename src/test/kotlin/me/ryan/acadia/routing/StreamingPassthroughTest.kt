package me.ryan.acadia.routing

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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
 * 발견 2: a long-lived streaming (SSE) response must pass through. The proxy read-timeout must
 * behave as a per-read idle timeout, so a stream whose chunks keep arriving (each gap under the
 * read-timeout) is not cancelled even though its total duration exceeds the read-timeout.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "15000")
class StreamingPassthroughTest {
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
            // Short read timeout; the stream's total duration exceeds it but each inter-chunk gap does not.
            registry.add("spring.cloud.gateway.server.webmvc.httpclient.read-timeout") { "2s" }
            registry.add("gateway.retry.retries") { 1 }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `청크가 read-timeout 이내 간격으로 도착하는 긴 스트림이 끝까지 전달된다`() {
        // 20 chunks across 4s (~200ms/chunk) -> total 4s > 2s read-timeout, each gap well under 2s.
        // A per-read idle timeout survives; a total-response timeout cancels at 2s.
        val body = (1..20).joinToString("") { "data: event-$it\n\n" }
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body)
                        .withChunkedDribbleDelay(20, 4000),
                ),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            // The final event only arrives at ~4s; receiving it proves the stream was not cancelled.
            .value { received -> require(received != null && received.contains("data: event-20")) { "stream truncated: $received" } }
    }
}
