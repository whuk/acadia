package me.ryan.acadia.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PrometheusEndpointTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `actuator prometheus 엔드포인트가 메트릭을 반환한다`() {
        webTestClient
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("text/plain;version=0.0.4;charset=utf-8")
    }

    @Test
    fun `요청 수 메트릭이 기록된다`() {
        // Given: actuator 엔드포인트에 요청
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk

        // When: prometheus 메트릭 조회
        val metricsResponse =
            webTestClient
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

        // Then: HTTP 요청 수 메트릭이 존재
        assertThat(metricsResponse).contains("http_server_requests_seconds_count")
    }

    @Test
    fun `응답 시간 메트릭이 기록된다`() {
        // Given: actuator 엔드포인트에 요청
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk

        // When: prometheus 메트릭 조회
        val metricsResponse =
            webTestClient
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

        // Then: HTTP 응답 시간 메트릭이 존재
        assertThat(metricsResponse).contains("http_server_requests_seconds_sum")
    }

    @Test
    fun `Circuit Breaker 상태 메트릭이 기록된다`() {
        // When: prometheus 메트릭 조회
        val metricsResponse =
            webTestClient
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

        // Then: Circuit Breaker 상태 메트릭이 존재
        assertThat(metricsResponse).contains("resilience4j_circuitbreaker_state")
    }
}
