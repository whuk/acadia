package me.ryan.acadia.observability

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import me.ryan.acadia.support.JwtTestSupport
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension::class)
class ResponseLoggingTest {
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
            registry.add("gateway.logging.include-headers") { true }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `응답 로그가 JSON 형식으로 기록된다`(output: CapturedOutput) {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .returnResult()

        val logOutput = output.toString()

        // Verify JSON format with required fields for RESPONSE
        assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
        assertThat(logOutput).contains("\"statusCode\":200")
        assertThat(logOutput).contains("\"requestId\":")
        assertThat(logOutput).contains("\"duration\":")
    }

    @Test
    fun `응답 헤더가 로그에 기록된다`(output: CapturedOutput) {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(
                    ok()
                        .withBody("""{"id": 1}""")
                        .withHeader("X-Custom-Header", "custom-value")
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .returnResult()

        val logOutput = output.toString()

        // Verify response headers are logged. Header names are matched case-insensitively because
        // the HTTP/1.1 proxy client normalizes downstream response header names to lowercase.
        assertThat(logOutput).contains("\"type\":\"RESPONSE\"")
        assertThat(logOutput).contains("\"headers\":")
        assertThat(logOutput).containsIgnoringCase("X-Custom-Header")
        assertThat(logOutput).contains("custom-value")
    }
}
