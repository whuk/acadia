package me.ryan.acadia.resilience

import me.ryan.acadia.support.JwtTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.net.ServerSocket

/**
 * SEC/REL-1c regression: when the backend connection is refused (service down), the gateway must
 * return 502 — not a 500 — and the body must not leak internal details (stack trace, backend host,
 * package names). This locks in the safe behavior already provided by Gateway MVC's proxy exchange.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
class BackendUnavailableTest {
    companion object {
        // A port that was bound then immediately released -> connections are refused deterministically.
        private val deadPort = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.services[0].name") { "user-service" }
            registry.add("gateway.services[0].path") { "/api/users/**" }
            registry.add("gateway.services[0].url") { "http://localhost:$deadPort" }
            registry.add("gateway.retry.retries") { 1 }
            // Disable the circuit breaker so the raw connection failure is observed.
            registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls") { 100 }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `백엔드 연결 거부 시 502를 반환하고 내부 정보를 노출하지 않는다`() {
        val result =
            webTestClient
                .get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
                .expectStatus()
                .isEqualTo(502)
                .expectBody()
                .returnResult()

        val body = result.responseBodyContent?.toString(Charsets.UTF_8) ?: ""
        assertThat(body).doesNotContain("Exception")
        assertThat(body).doesNotContain("me.ryan.acadia")
        assertThat(body).doesNotContain("localhost:$deadPort")
    }
}
