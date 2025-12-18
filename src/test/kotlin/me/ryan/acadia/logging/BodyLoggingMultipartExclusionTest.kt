package me.ryan.acadia.logging

import com.github.tomakehurst.wiremock.client.WireMock.aMultipart
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension::class)
class BodyLoggingMultipartExclusionTest {
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
    fun `multipart form-data 요청은 바디 로깅에서 제외된다`(output: CapturedOutput) {
        wireMock.stubFor(
            post(urlPathMatching("/users/upload"))
                .withMultipartRequestBody(aMultipart())
                .willReturn(ok().withBody("""{"status": "uploaded"}""")),
        )

        val multipartBodyBuilder = MultipartBodyBuilder()
        multipartBodyBuilder.part(
            "file",
            object : ByteArrayResource("test file content".toByteArray()) {
                override fun getFilename(): String = "test.txt"
            },
        )
        multipartBodyBuilder.part("description", "Test file upload")

        webTestClient
            .post()
            .uri("/api/users/upload")
            .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipartBodyBuilder.build())
            .exchange()
            .expectStatus()
            .isOk

        val logOutput = output.toString()

        // Verify request is logged
        assertThat(logOutput).contains("\"type\":\"REQUEST\"")
        assertThat(logOutput).contains("/api/users/upload")

        // Verify multipart body is NOT logged (should not contain the file content or multipart boundary)
        assertThat(logOutput).doesNotContain("test file content")
        assertThat(logOutput).doesNotContain("Test file upload")

        // The REQUEST log entry should not have a body field with multipart data
        val requestLogLine = logOutput.lines().find { it.contains("\"type\":\"REQUEST\"") && it.contains("/api/users/upload") }
        assertThat(requestLogLine).isNotNull
        // Body should be excluded or null for multipart requests
        assertThat(requestLogLine).satisfiesAnyOf(
            { line -> assertThat(line).doesNotContain("\"body\":") },
            { line -> assertThat(line).contains("\"body\":null") },
        )
    }
}
