package me.ryan.acadia.ratelimit

import me.ryan.acadia.filter.RateLimitFilter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RateLimitTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var rateLimitFilter: RateLimitFilter

    @BeforeEach
    fun setUp() {
        rateLimitFilter.reset()
    }

    @Test
    fun `버스트 20 요청까지 허용된다`() {
        // Given: 버스트 허용량 20개까지 요청
        repeat(20) {
            webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk
        }

        // When: 21번째 요청 (버스트 초과)
        // Then: 429 Too Many Requests 반환
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }

    @Test
    fun `Rate Limit 헤더가 응답에 포함된다`() {
        // When: 요청 전송
        // Then: Rate Limit 헤더들이 응답에 포함됨
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .exists(RateLimitFilter.HEADER_LIMIT)
            .expectHeader()
            .exists(RateLimitFilter.HEADER_REMAINING)
    }

    @Test
    fun `Rate Limit 리셋 시간이 헤더에 포함된다`() {
        // When: 요청 전송
        // Then: X-RateLimit-Reset 헤더가 응답에 포함됨
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .exists(RateLimitFilter.HEADER_RESET)
    }
}
