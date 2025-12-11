package me.ryan.acadia.routing

import com.github.tomakehurst.wiremock.client.WireMock.get
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UserServiceRoutingTest {
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
    fun `users 경로가 user-service로 라우팅된다`() {
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
            .jsonPath("$.id")
            .isEqualTo(1)
    }
}
