package me.ryan.acadia.support

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Date

/**
 * 개발/테스트용 JWT 토큰 생성 테스트.
 *
 * 이 테스트는 실제 테스트가 아닌 개발 편의를 위한 유틸리티입니다.
 * 필요할 때 @Disabled를 제거하고 실행하여 토큰을 생성할 수 있습니다.
 *
 * 사용법:
 * 1. 원하는 테스트 메서드의 @Disabled 제거
 * 2. 필요한 파라미터 수정
 * 3. 테스트 실행: ./gradlew test --tests "JwtTokenGeneratorTest.개발용*"
 * 4. 콘솔 출력에서 토큰 복사
 */
class JwtTokenGeneratorTest {
    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `개발용 JWT 토큰 생성 - 기본`() {
        val token = JwtTestSupport.generateToken()

        printToken(
            token = token,
            description = "기본 토큰 (userId: test-user, roles: 없음, 만료: 1시간)",
        )
    }

    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `개발용 JWT 토큰 생성 - 관리자`() {
        val token =
            JwtTestSupport.generateToken(
                userId = "admin-user",
                roles = listOf("ADMIN", "USER"),
                expirationMs = 86400000, // 24시간
            )

        printToken(
            token = token,
            description = "관리자 토큰 (userId: admin-user, roles: ADMIN,USER, 만료: 24시간)",
        )
    }

    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `개발용 JWT 토큰 생성 - 일반 사용자`() {
        val token =
            JwtTestSupport.generateToken(
                userId = "user-123",
                roles = listOf("USER"),
                expirationMs = 3600000, // 1시간
            )

        printToken(
            token = token,
            description = "일반 사용자 토큰 (userId: user-123, roles: USER, 만료: 1시간)",
        )
    }

    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `개발용 JWT 토큰 생성 - 장기 유효`() {
        val token =
            JwtTestSupport.generateToken(
                userId = "dev-user",
                roles = listOf("USER"),
                expirationMs = 604800000, // 7일
            )

        printToken(
            token = token,
            description = "장기 유효 토큰 (userId: dev-user, roles: USER, 만료: 7일)",
        )
    }

    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `개발용 JWT 토큰 생성 - 커스텀`() {
        // 필요에 따라 수정하세요
        val token =
            JwtTestSupport.generateToken(
                userId = "custom-user-id",
                roles = listOf("ROLE1", "ROLE2"),
                expirationMs = 3600000,
                additionalClaims =
                    mapOf(
                        "email" to "user@example.com",
                        "tenant" to "tenant-123",
                    ),
            )

        printToken(
            token = token,
            description = "커스텀 토큰 (추가 클레임 포함)",
        )
    }

    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `만료된 JWT 토큰 생성`() {
        val token =
            JwtTestSupport.generateExpiredToken(
                userId = "expired-user",
                roles = listOf("USER"),
            )

        printToken(
            token = token,
            description = "만료된 토큰 (테스트용)",
        )
    }

    @Test
    @Disabled("개발 시 필요할 때만 활성화")
    fun `커스텀 시크릿 키로 JWT 토큰 생성`() {
        // 프로덕션 환경의 시크릿 키로 토큰을 생성해야 할 때 사용
        val customSecret = "your-production-secret-key-at-least-32-bytes!"
        val secretKey = Keys.hmacShaKeyFor(customSecret.toByteArray())

        val token =
            Jwts
                .builder()
                .subject("user-123")
                .claim("roles", listOf("USER"))
                .issuedAt(Date())
                .expiration(Date(System.currentTimeMillis() + 3600000))
                .signWith(secretKey)
                .compact()

        printToken(
            token = token,
            description = "커스텀 시크릿 키 토큰",
            secret = customSecret,
        )
    }

    private fun printToken(
        token: String,
        description: String,
        secret: String = JwtTestSupport.SECRET,
    ) {
        val parts = token.split(".")
        val header = String(Base64.getUrlDecoder().decode(parts[0]))
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))

        println(
            """
            |
            |╔══════════════════════════════════════════════════════════════════════════════╗
            |║                           JWT TOKEN GENERATOR                                 ║
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║ Description: $description
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║ SECRET KEY:
            |║ $secret
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║ TOKEN:
            |║ $token
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║ HEADER:
            |║ $header
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║ PAYLOAD:
            |║ $payload
            |╠══════════════════════════════════════════════════════════════════════════════╣
            |║ CURL EXAMPLE:
            |║ curl -H "Authorization: Bearer $token" http://localhost:8080/api/users/1
            |╚══════════════════════════════════════════════════════════════════════════════╝
            |
            """.trimMargin(),
        )
    }
}
