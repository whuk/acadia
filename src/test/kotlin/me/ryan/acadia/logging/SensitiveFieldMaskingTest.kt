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
class SensitiveFieldMaskingTest {
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
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `민감한 필드는 마스킹 처리된다`(output: CapturedOutput) {
        val requestBody = """{"username": "testuser", "password": "secret123", "token": "abc-token-xyz"}"""

        wireMock.stubFor(
            post(urlPathMatching("/users"))
                .willReturn(
                    ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{"id": 1, "accessToken": "jwt-token-value", "refreshToken": "refresh-value"}"""),
                ),
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

        await untilAsserted {
            val logOutput = output.toString()

            // Verify request body is logged with masking
            assertThat(logOutput).contains("\"type\":\"REQUEST\"")
            assertThat(logOutput).contains("testuser")
            assertThat(logOutput).doesNotContain("secret123")
            assertThat(logOutput).doesNotContain("abc-token-xyz")
            assertThat(logOutput).contains("***MASKED***")

            // Verify response body is logged with masking
            assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
            assertThat(logOutput).doesNotContain("jwt-token-value")
            assertThat(logOutput).doesNotContain("refresh-value")
        }
    }
}
