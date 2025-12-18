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
class BodyLoggingMaxSizeTest {
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
            registry.add("gateway.logging.include-body") { true }
            registry.add("gateway.logging.max-body-size") { 50 }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `요청 바디 크기가 최대 크기를 초과하면 잘라서 기록된다`(output: CapturedOutput) {
        // Create a body larger than 50 bytes
        val requestBody = """{"username": "verylongusername", "email": "verylongemail@example.com", "extra": "data"}"""

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

        val logOutput = output.toString()

        // Verify request body is truncated and contains truncation indicator
        assertThat(logOutput).contains("\"type\":\"REQUEST\"")
        assertThat(logOutput).contains("\"body\":")
        assertThat(logOutput).contains("...[TRUNCATED]")
        // The truncated body should not contain the end of the original body
        assertThat(logOutput).doesNotContain("extra")
    }

    @Test
    fun `응답 바디 크기가 최대 크기를 초과하면 잘라서 기록된다`(output: CapturedOutput) {
        // Create a response body larger than 50 bytes
        val responseBody = """{"id": 123, "status": "created", "message": "This is a very long message that exceeds the limit"}"""

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
            .bodyValue("""{"test": "data"}""")
            .exchange()
            .expectStatus()
            .isOk

        // Verify response body is truncated and contains truncation indicator
        await untilAsserted {
            val logOutput = output.toString()
            assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
            assertThat(logOutput).contains("\"body\":")
            assertThat(logOutput).contains("...[TRUNCATED]")
            // The truncated body should not contain the end of the original body
            assertThat(logOutput).doesNotContain("exceeds the limit")
        }
    }
}
