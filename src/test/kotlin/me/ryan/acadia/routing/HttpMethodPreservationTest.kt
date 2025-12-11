package me.ryan.acadia.routing

import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import me.ryan.acadia.support.JwtTestSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HttpMethodPreservationTest {
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
    fun `POST 메서드가 라우팅 시 유지된다`() {
        wireMock.stubFor(
            post(urlPathMatching("/users"))
                .willReturn(ok().withBody("""{"id": 1, "name": "created"}""")),
        )

        webTestClient
            .post()
            .uri("/api/users")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("created")
    }

    @Test
    fun `PUT 메서드가 라우팅 시 유지된다`() {
        wireMock.stubFor(
            put(urlPathMatching("/users/1"))
                .willReturn(ok().withBody("""{"id": 1, "name": "updated"}""")),
        )

        webTestClient
            .put()
            .uri("/api/users/1")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("updated")
    }

    @Test
    fun `DELETE 메서드가 라우팅 시 유지된다`() {
        wireMock.stubFor(
            delete(urlPathMatching("/users/1"))
                .willReturn(ok().withBody("""{"deleted": true}""")),
        )

        webTestClient
            .delete()
            .uri("/api/users/1")
            .header("Authorization", JwtTestSupport.validAuthHeader())
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.deleted")
            .isEqualTo(true)
    }
}
