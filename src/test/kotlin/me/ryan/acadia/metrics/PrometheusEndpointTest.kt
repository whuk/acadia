package me.ryan.acadia.metrics

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
}
