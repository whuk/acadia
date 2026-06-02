package me.ryan.acadia.logging

import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import me.ryan.acadia.support.JwtTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension::class)
class BodyLoggingDisabledTest {
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
            registry.add("gateway.logging.enabled") { true }
            registry.add("gateway.logging.include-body") { false }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `바디 로깅이 비활성화되면 요청 바디가 로그에 포함되지 않는다`(output: CapturedOutput) {
        val requestBody = """{"username": "testuser", "secret": "password123"}"""

        wireMock.stubFor(
            post(urlPathMatching("/users"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .post()
            .uri("/api/users")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus()
            .isOk

        // Verify request log exists but body is not included
        await untilAsserted {
            val logOutput = output.toString()
            assertThat(logOutput).contains("\"type\":\"REQUEST\"")
            assertThat(logOutput).doesNotContain("testuser")
            assertThat(logOutput).doesNotContain("password123")
        }
    }

    @Test
    fun `바디 로깅이 비활성화되면 응답 바디가 로그에 포함되지 않는다`(output: CapturedOutput) {
        val responseBody = """{"id": 456, "status": "success"}"""

        wireMock.stubFor(
            post(urlPathMatching("/users"))
                .willReturn(
                    ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(responseBody),
                ),
        )

        webTestClient
            .post()
            .uri("/api/users")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"username": "test"}""")
            .exchange()
            .expectStatus()
            .isOk

        // Verify response log exists but body is not included
        await untilAsserted {
            val logOutput = output.toString()
            assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
            // Use body-specific patterns; a bare "456" can collide with the duration value (ms).
            assertThat(logOutput).doesNotContain("\"id\": 456")
            assertThat(logOutput).doesNotContain("success")
        }
    }
}
