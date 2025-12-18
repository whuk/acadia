package me.ryan.acadia.support

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date

/**
 * JWT 토큰 생성을 위한 테스트 지원 유틸리티.
 *
 * 이 클래스는 테스트 목적으로만 사용됩니다.
 * Gateway는 JWT 검증만 수행하며, 실제 토큰 발급은 인증 서비스에서 담당합니다.
 */
object JwtTestSupport {
    const val SECRET = "default-secret-key-for-testing-purposes-only-32bytes"
    private val secretKey = Keys.hmacShaKeyFor(SECRET.toByteArray())

    private const val DEFAULT_USER_ID = "test-user"
    private const val DEFAULT_EXPIRATION_MS = 3600000L // 1시간

    /**
     * JWT 토큰을 생성합니다.
     *
     * @param userId 사용자 ID (subject 클레임으로 설정, X-User-Id 헤더로 전달됨)
     * @param roles 사용자 역할 목록 (roles 클레임으로 설정, X-User-Roles 헤더로 전달됨)
     * @param expirationMs 토큰 만료 시간 (밀리초, 기본값: 1시간)
     * @param additionalClaims 추가 클레임
     * @return JWT 토큰 문자열
     */
    fun generateToken(
        userId: String = DEFAULT_USER_ID,
        roles: List<String> = emptyList(),
        expirationMs: Long = DEFAULT_EXPIRATION_MS,
        additionalClaims: Map<String, Any> = emptyMap(),
    ): String =
        Jwts
            .builder()
            .subject(userId)
            .apply {
                if (roles.isNotEmpty()) {
                    claim("roles", roles)
                }
                additionalClaims.forEach { (key, value) ->
                    claim(key, value)
                }
            }.issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(secretKey)
            .compact()

    /**
     * 기본 유효한 토큰을 생성합니다. (하위 호환성 유지)
     */
    fun generateValidToken(
        subject: String = DEFAULT_USER_ID,
        expirationMs: Long = DEFAULT_EXPIRATION_MS,
    ): String = generateToken(userId = subject, expirationMs = expirationMs)

    /**
     * 만료된 토큰을 생성합니다.
     */
    fun generateExpiredToken(
        userId: String = DEFAULT_USER_ID,
        roles: List<String> = emptyList(),
    ): String = generateToken(userId = userId, roles = roles, expirationMs = -1000)

    /**
     * Authorization 헤더 값을 생성합니다. (Bearer 접두사 포함)
     */
    fun validAuthHeader(): String = "Bearer ${generateToken()}"

    /**
     * 지정된 사용자 ID와 역할로 Authorization 헤더 값을 생성합니다.
     */
    fun authHeader(
        userId: String = DEFAULT_USER_ID,
        roles: List<String> = emptyList(),
    ): String = "Bearer ${generateToken(userId = userId, roles = roles)}"
}
