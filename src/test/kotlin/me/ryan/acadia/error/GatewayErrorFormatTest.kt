package me.ryan.acadia.error

import me.ryan.acadia.support.JwtTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.ServerSocket

/**
 * B3 / rule gateway-filter section 6: gateway error bodies are unified to RFC 9457 ProblemDetail
 * (application/problem+json). The error-dispatch path (proxy 5xx, undefined routes) must not fall
 * back to Spring's default error JSON.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
class GatewayErrorFormatTest {
    companion object {
        private val deadPort = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.services[0].name") { "user-service" }
            registry.add("gateway.services[0].path") { "/api/users/**" }
            registry.add("gateway.services[0].url") { "http://localhost:$deadPort" }
            registry.add("gateway.retry.retries") { 1 }
            registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls") { 100 }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `백엔드 연결 거부 시 problem+json ProblemDetail 502를 반환한다`() {
        val result =
            webTestClient
                .get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
                .expectStatus()
                .isEqualTo(502)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .returnResult()

        val body = result.responseBodyContent?.toString(Charsets.UTF_8) ?: ""
        assertThat(body).contains("\"title\"")
        assertThat(body).contains("\"status\":502")
        // no internal leak
        assertThat(body).doesNotContain("Exception")
        assertThat(body).doesNotContain("me.ryan.acadia")
        assertThat(body).doesNotContain("localhost:$deadPort")
    }
}
