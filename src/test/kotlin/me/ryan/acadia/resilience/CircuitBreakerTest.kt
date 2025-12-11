package me.ryan.acadia.resilience

import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
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
class CircuitBreakerTest {
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
            // Circuit breaker config: 50% failure rate threshold with 4 calls minimum
            registry.add("resilience4j.circuitbreaker.instances.gatewayCircuitBreaker.sliding-window-size") { 4 }
            registry.add("resilience4j.circuitbreaker.instances.gatewayCircuitBreaker.minimum-number-of-calls") { 4 }
            registry.add("resilience4j.circuitbreaker.instances.gatewayCircuitBreaker.failure-rate-threshold") { 50 }
            registry.add("resilience4j.circuitbreaker.instances.gatewayCircuitBreaker.wait-duration-in-open-state") { "60s" }
            registry.add("resilience4j.circuitbreaker.instances.gatewayCircuitBreaker.permitted-number-of-calls-in-half-open-state") { 1 }
            // Keep retry at 1 to avoid retry interference (1 means no additional retries)
            registry.add("gateway.retry.retries") { 1 }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `실패율 50% 초과 시 Circuit Breaker가 열린다`() {
        // Setup: Backend always returns 500 error
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(serverError()),
        )

        // Make enough failed requests to exceed 50% failure rate threshold
        // With minimum-number-of-calls=4, we need 4 calls to evaluate
        // With 50% threshold, more than 2 failures out of 4 will trip the breaker
        repeat(4) {
            webTestClient
                .get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
        }

        // After failures exceed threshold (100% > 50%), circuit breaker should open
        // The next request should get 503 Service Unavailable
        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isEqualTo(503)
    }

    @Test
    fun `Circuit Breaker 열린 상태에서 503을 반환한다`() {
        // Setup: Backend always returns 500 error
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(serverError()),
        )

        // Trip the circuit breaker by making enough failed requests
        repeat(4) {
            webTestClient
                .get()
                .uri("/api/users/2")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
                .exchange()
        }

        // Reset WireMock request count to verify no backend calls are made
        wireMock.resetRequests()

        // When circuit breaker is open, request should return 503 immediately
        // WITHOUT calling the backend
        webTestClient
            .get()
            .uri("/api/users/2")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isEqualTo(503)

        // Verify: No request was sent to backend (circuit breaker blocked it)
        wireMock.verify(exactly(0), getRequestedFor(urlPathMatching("/users/.*")))
    }
}
