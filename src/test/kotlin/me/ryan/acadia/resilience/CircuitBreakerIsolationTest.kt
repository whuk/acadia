package me.ryan.acadia.resilience

import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.serverError
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
@AutoConfigureWebTestClient(timeout = "10000")
class CircuitBreakerIsolationTest {
    companion object {
        @JvmField
        @RegisterExtension
        val userServiceMock: WireMockExtension = WireMockExtension.newInstance().build()

        @JvmField
        @RegisterExtension
        val orderServiceMock: WireMockExtension = WireMockExtension.newInstance().build()

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.services[0].name") { "user-service" }
            registry.add("gateway.services[0].path") { "/api/users/**" }
            registry.add("gateway.services[0].url") { userServiceMock.baseUrl() }
            registry.add("gateway.services[0].has-public-path") { true }
            registry.add("gateway.services[1].name") { "order-service" }
            registry.add("gateway.services[1].path") { "/api/orders/**" }
            registry.add("gateway.services[1].url") { orderServiceMock.baseUrl() }
            // Circuit breaker default config: 50% failure rate threshold with 4 calls minimum
            registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size") { 4 }
            registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls") { 4 }
            registry.add("resilience4j.circuitbreaker.configs.default.failure-rate-threshold") { 50 }
            registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state") { "60s" }
            registry.add("resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state") { 1 }
            // Keep retry at 1 to avoid retry interference (1 means no additional retries)
            registry.add("gateway.retry.retries") { 1 }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `user-service 서킷이 열려도 order-service 호출은 정상 동작한다`() {
        // Setup: user-service always fails, order-service is healthy
        userServiceMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(serverError()),
        )
        orderServiceMock.stubFor(
            get(urlPathMatching("/orders/.*"))
                .willReturn(ok()),
        )

        // Trip the user-service circuit breaker: 5 failures cover both the
        // per-service default (minimum-number-of-calls=4) and the legacy shared
        // instance config (minimum-number-of-calls=5)
        repeat(5) {
            webTestClient
                .get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
        }

        // user-service circuit is open now
        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isEqualTo(503)

        // order-service must NOT be affected by the open user-service circuit
        webTestClient
            .get()
            .uri("/api/orders/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `서킷이 열린 user-service 호출은 백엔드에 도달하지 않고 503을 반환한다`() {
        // Setup: user-service always fails
        userServiceMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(serverError()),
        )

        // Trip the user-service circuit breaker (calls may already be short-circuited
        // if another test opened this circuit first — either way it ends up open)
        repeat(5) {
            webTestClient
                .get()
                .uri("/api/users/2")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
        }

        // Reset WireMock request count to verify no backend calls are made
        userServiceMock.resetRequests()

        // When the circuit is open, the request returns 503 immediately
        webTestClient
            .get()
            .uri("/api/users/2")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isEqualTo(503)

        // Verify: no request was sent to the backend (circuit breaker blocked it)
        userServiceMock.verify(exactly(0), getRequestedFor(urlPathMatching("/users/.*")))
    }

    @Test
    fun `main 라우트 실패로 열린 서킷이 같은 서비스의 public 라우트도 차단한다`() {
        // Setup: user-service always fails
        userServiceMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(serverError()),
        )

        // Trip the circuit through the main (authenticated) route
        repeat(5) {
            webTestClient
                .get()
                .uri("/api/users/3")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
        }

        // Reset WireMock request count to verify no backend calls are made
        userServiceMock.resetRequests()

        // The public route shares the same cb-user-service instance, so it is blocked too
        webTestClient
            .get()
            .uri("/api/public/users/3")
            .exchange()
            .expectStatus()
            .isEqualTo(503)

        // Verify: the public request never reached the backend
        userServiceMock.verify(exactly(0), getRequestedFor(urlPathMatching("/users/.*")))
    }
}
