package me.ryan.acadia.filter

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import me.ryan.acadia.config.JwtProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.HttpHeaders
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.Date

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class JwtAuthenticationTest {
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
            registry.add("gateway.services[0].has-public-path") { true }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var jwtProperties: JwtProperties

    @Test
    fun `Authorization 헤더 없는 요청은 401을 반환한다`() {
        webTestClient
            .get()
            .uri("/api/users/1")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `잘못된 JWT 토큰은 401을 반환한다`() {
        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `만료된 JWT 토큰은 401을 반환한다`() {
        val expiredToken = createExpiredToken()

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `유효한 JWT 토큰은 라우팅이 진행된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        val validToken = createValidToken()

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `JWT에서 추출한 사용자 ID가 X-User-Id 헤더로 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        val userId = "user-123"
        val validToken = createValidTokenWithSubject(userId)

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-User-Id", equalTo(userId)),
        )
    }

    @Test
    fun `JWT에서 추출한 역할이 X-User-Roles 헤더로 전달된다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        val roles = listOf("admin", "user")
        val validToken = createValidTokenWithRoles(roles)

        webTestClient
            .get()
            .uri("/api/users/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
            .exchange()
            .expectStatus()
            .isOk

        wireMock.verify(
            getRequestedFor(urlPathMatching("/users/.*"))
                .withHeader("X-User-Roles", equalTo("admin,user")),
        )
    }

    @Test
    fun `공개 경로는 인증 없이 접근 가능하다`() {
        wireMock.stubFor(
            get(urlPathMatching("/users/.*"))
                .willReturn(ok().withBody("""{"id": 1}""")),
        )

        webTestClient
            .get()
            .uri("/api/public/users/1")
            .exchange()
            .expectStatus()
            .isOk
    }

    private fun createValidTokenWithRoles(roles: List<String>): String {
        val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
        val futureDate = Date(System.currentTimeMillis() + 3600000)

        return Jwts
            .builder()
            .subject("test-user")
            .claim("roles", roles)
            .expiration(futureDate)
            .signWith(secretKey)
            .compact()
    }

    private fun createValidTokenWithSubject(subject: String): String {
        val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
        val futureDate = Date(System.currentTimeMillis() + 3600000)

        return Jwts
            .builder()
            .subject(subject)
            .expiration(futureDate)
            .signWith(secretKey)
            .compact()
    }

    private fun createValidToken(): String {
        val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
        val futureDate = Date(System.currentTimeMillis() + 3600000) // 1 hour later

        return Jwts
            .builder()
            .subject("test-user")
            .expiration(futureDate)
            .signWith(secretKey)
            .compact()
    }

    private fun createExpiredToken(): String {
        val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
        val pastDate = Date(System.currentTimeMillis() - 3600000) // 1 hour ago

        return Jwts
            .builder()
            .subject("test-user")
            .expiration(pastDate)
            .signWith(secretKey)
            .compact()
    }
}
