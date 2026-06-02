package me.ryan.acadia.filter

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import me.ryan.acadia.support.JwtTestSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * SEC-2: client-supplied trusted headers (X-User-Id/X-User-Roles) must never reach downstream;
 * the gateway derives them solely from the verified JWT.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtHeaderInjectionTest {
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
    fun `roles 없는 토큰에서 클라이언트가 주입한 X-User-Roles는 다운스트림에 전달되지 않는다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        // Token without roles claim
        val token = JwtTestSupport.generateToken(userId = "user-1")

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("X-User-Roles", "ADMIN") // spoof attempt
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-User-Roles", absent()),
        )
    }

    @Test
    fun `클라이언트가 주입한 X-User-Id는 JWT subject로 덮어써진다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        val token = JwtTestSupport.generateToken(userId = "real-user")

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("X-User-Id", "admin-spoof") // spoof attempt
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-User-Id", equalTo("real-user")),
        )
    }
}
