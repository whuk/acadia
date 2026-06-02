package me.ryan.acadia.observability

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.notMatching
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
 * B1 / rule section 5: SpanId is regenerated per hop; a valid inbound SpanId becomes the
 * X-B3-ParentSpanId so the gateway span links to its caller. A client cannot spoof the parent.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class B3SpanIdPropagationTest {
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

    private fun callWithHeaders(vararg headers: Pair<String, String>) {
        wireMock.stubFor(get(urlPathMatching("/users/.*")).willReturn(ok()))
        var spec =
            webTestClient
                .get()
                .uri("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, JwtTestSupport.validAuthHeader())
        headers.forEach { (k, v) -> spec = spec.header(k, v) }
        spec.exchange().expectStatus().isOk
    }

    @Test
    fun `인입 SpanId가 있어도 백엔드로는 새 SpanId가 전달된다`() {
        callWithHeaders("X-B3-SpanId" to "1111111111111111")

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-B3-SpanId", matching("[a-f0-9]{16}"))
                .withHeader("X-B3-SpanId", notMatching("1111111111111111")),
        )
    }

    @Test
    fun `유효한 인입 SpanId는 X-B3-ParentSpanId로 전달된다`() {
        callWithHeaders("X-B3-SpanId" to "1111111111111111")

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-B3-ParentSpanId", equalTo("1111111111111111")),
        )
    }

    @Test
    fun `인입 SpanId가 없으면 클라이언트가 주입한 ParentSpanId는 제거된다`() {
        callWithHeaders("X-B3-ParentSpanId" to "deadbeefdeadbeef")

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-B3-ParentSpanId", absent()),
        )
    }
}
