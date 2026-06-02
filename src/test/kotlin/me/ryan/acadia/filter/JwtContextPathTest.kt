package me.ryan.acadia.filter

import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * SEC-1a regression: with a servlet context path set, the JWT filter must still authenticate
 * the gateway-relative protected paths instead of treating them as exempt.
 *
 * Note: `@AutoConfigureWebTestClient` already prefixes the configured base URL with the context
 * path, so request URIs here are written WITHOUT the `/gateway` prefix. The context path must be
 * a static `properties` entry (a `@DynamicPropertySource` value is applied too late to take effect).
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = ["server.servlet.context-path=/gateway"],
)
@AutoConfigureWebTestClient
class JwtContextPathTest {
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
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `컨텍스트 패스 하의 보호 경로는 토큰 없으면 401을 반환한다`() {
        webTestClient
            .get()
            .uri("/api/users/1")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}
