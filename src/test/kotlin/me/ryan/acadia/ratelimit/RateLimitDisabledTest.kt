package me.ryan.acadia.ratelimit

import me.ryan.acadia.filter.RateLimitFilter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = ["gateway.rate-limit.enabled=false"])
class RateLimitDisabledTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var rateLimitFilter: RateLimitFilter

    @BeforeEach
    fun setUp() {
        rateLimitFilter.reset()
    }

    @Test
    fun `Rate Limiting이 비활성화되면 제한 없이 요청이 통과한다`() {
        // Given: Rate Limiting이 비활성화됨 (enabled=false)
        // When: 버스트 허용량(20)을 초과하는 25개 요청
        // Then: 모든 요청이 200 OK로 통과
        repeat(25) {
            webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk
        }
    }

    @Test
    fun `Rate Limiting이 비활성화되면 Rate Limit 헤더가 응답에 포함되지 않는다`() {
        // Given: Rate Limiting이 비활성화됨 (enabled=false)
        // When: 요청 전송
        // Then: Rate Limit 헤더들이 응답에 포함되지 않음
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .doesNotExist(RateLimitFilter.HEADER_LIMIT)
            .expectHeader()
            .doesNotExist(RateLimitFilter.HEADER_REMAINING)
            .expectHeader()
            .doesNotExist(RateLimitFilter.HEADER_RESET)
    }
}
