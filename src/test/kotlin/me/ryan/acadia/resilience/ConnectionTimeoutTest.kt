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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "5000")
class ConnectionTimeoutTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.services[0].name") { "user-service" }
            registry.add("gateway.services[0].path") { "/api/users/**" }
            // 10.255.255.1 is a non-routable IP that will cause connection timeout
            registry.add("gateway.services[0].url") { "http://10.255.255.1:8081" }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `백엔드 연결 실패 시 빠르게 5xx를 반환한다`() {
        var status = 0
        val elapsed =
            measureTime {
                status =
                    webTestClient
                        .get()
                        .uri("/api/users/1")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                        .exchange()
                        .returnResult(String::class.java)
                        .status
                        .value()
            }

        // A connect failure surfaces as 502 or 504 depending on how the OS/HTTP client classifies
        // an unreachable host; either is a valid gateway 5xx. The key guarantee is that it returns
        // around the connect timeout (~1s) instead of hanging until the read timeout (3s).
        assertThat(status).isIn(502, 504)
        assertThat(elapsed).isLessThan(2.seconds)
        assertThat(elapsed).isGreaterThan(800.milliseconds)
    }
}
