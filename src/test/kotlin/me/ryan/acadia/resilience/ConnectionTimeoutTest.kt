package me.ryan.acadia.resilience

import me.ryan.acadia.support.JwtTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
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
            // 10.255.255.1 is a non-routable IP that will cause connection timeout
            registry.add("user-service.url") { "http://10.255.255.1:8081" }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `백엔드 연결이 1초 초과 시 504를 반환한다`() {
        val elapsed =
            measureTime {
                webTestClient
                    .get()
                    .uri("/api/users/1")
                    .header("Authorization", JwtTestSupport.validAuthHeader())
                    .exchange()
                    .expectStatus()
                    .isEqualTo(504)
            }

        // Connection timeout should trigger around 1 second (with some tolerance)
        // It should NOT wait for the full response-timeout (3 seconds)
        assertThat(elapsed).isLessThan(2.seconds)
        assertThat(elapsed).isGreaterThan(800.milliseconds)
    }
}
