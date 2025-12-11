# Phase 3: 인증 (JWT)

## 목표
JWT 기반 인증 구현 및 사용자 정보 헤더 전달

## PRD 요구사항
- Authorization: Bearer JWT 검증
- 유효성 검증 실패 시 401
- X-User-Id, X-User-Roles 헤더로 사용자 정보 전달
- 공개 경로(/api/public/**)는 인증 없이 접근 가능

## 테스트 목록

### 3.1 Authorization 헤더 없는 요청은 401을 반환한다
```kotlin
@Test
fun `Authorization 헤더 없는 요청은 401을 반환한다`() {
    webTestClient
        .get()
        .uri("/api/users/1")
        .exchange()
        .expectStatus()
        .isUnauthorized
}
```

### 3.2 잘못된 JWT 토큰은 401을 반환한다
```kotlin
@Test
fun `잘못된 JWT 토큰은 401을 반환한다`() {
    webTestClient
        .get()
        .uri("/api/users/1")
        .header("Authorization", "Bearer invalid-token")
        .exchange()
        .expectStatus()
        .isUnauthorized
}
```

### 3.3 만료된 JWT 토큰은 401을 반환한다
```kotlin
@Test
fun `만료된 JWT 토큰은 401을 반환한다`() {
    val expiredToken = createExpiredToken()

    webTestClient
        .get()
        .uri("/api/users/1")
        .header("Authorization", "Bearer $expiredToken")
        .exchange()
        .expectStatus()
        .isUnauthorized
}
```

### 3.4 유효한 JWT 토큰은 라우팅이 진행된다
```kotlin
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
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus()
        .isOk
}
```

### 3.5 JWT에서 추출한 사용자 ID가 X-User-Id 헤더로 전달된다
```kotlin
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
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus()
        .isOk

    wireMock.verify(
        getRequestedFor(urlPathMatching("/users/.*"))
            .withHeader("X-User-Id", equalTo(userId)),
    )
}
```

### 3.6 JWT에서 추출한 역할이 X-User-Roles 헤더로 전달된다
```kotlin
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
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus()
        .isOk

    wireMock.verify(
        getRequestedFor(urlPathMatching("/users/.*"))
            .withHeader("X-User-Roles", equalTo("admin,user")),
    )
}
```

### 3.7 공개 경로(/api/public/**)는 인증 없이 접근 가능하다
```kotlin
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
```

## 구현 가이드

### JWT 설정 (application.yml)
```yaml
jwt:
  secret: your-secret-key-must-be-at-least-32-bytes-long
```

### JwtProperties.kt
```kotlin
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "default-secret-key-for-testing-purposes-only-32bytes",
)
```

### JwtAuthenticationFilter.kt
```kotlin
@Component
class JwtAuthenticationFilter(
    private val jwtProperties: JwtProperties,
) : GlobalFilter,
    Ordered {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val X_USER_ID_HEADER = "X-User-Id"
        private const val X_USER_ROLES_HEADER = "X-User-Roles"
        private const val ROLES_CLAIM = "roles"
        private const val PUBLIC_PATH_PREFIX = "/api/public/"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        val path = exchange.request.path.value()
        if (path.startsWith(PUBLIC_PATH_PREFIX)) {
            return chain.filter(exchange)
        }

        val authHeader =
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?: return unauthorized(exchange)

        val token = extractToken(authHeader) ?: return unauthorized(exchange)

        val claims = parseToken(token) ?: return unauthorized(exchange)

        val roles = extractRoles(claims)

        val mutatedExchange =
            exchange
                .mutate()
                .request { request ->
                    request.header(X_USER_ID_HEADER, claims.subject)
                    if (roles.isNotEmpty()) {
                        request.header(X_USER_ROLES_HEADER, roles.joinToString(","))
                    }
                }.build()

        return chain.filter(mutatedExchange)
    }

    private fun extractToken(authHeader: String): String? =
        authHeader
            .takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)

    private fun extractRoles(claims: Claims): List<String> {
        val roles = claims[ROLES_CLAIM] ?: return emptyList()
        return when (roles) {
            is List<*> -> roles.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun parseToken(token: String): Claims? =
        try {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: JwtException) {
            null
        }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    override fun getOrder(): Int = -100
}
```

### 공개 경로 라우팅 (GatewayConfig.kt)
```kotlin
@Bean
fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator =
    builder
        .routes()
        .route("public-user-service") { r ->
            r
                .path("/api/public/users/**")
                .filters { f -> f.stripPrefix(2).preserveHostHeader() }
                .uri(userServiceUrl)
        }
        // ... 기타 라우트
        .build()
```

## 테스트 유틸리티

### 토큰 생성 헬퍼 (테스트 클래스 내)
```kotlin
private fun createValidToken(): String {
    val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    val futureDate = Date(System.currentTimeMillis() + 3600000)

    return Jwts
        .builder()
        .subject("test-user")
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

private fun createExpiredToken(): String {
    val secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    val pastDate = Date(System.currentTimeMillis() - 3600000)

    return Jwts
        .builder()
        .subject("test-user")
        .expiration(pastDate)
        .signWith(secretKey)
        .compact()
}
```

## 완료 조건
- [x] 모든 인증 테스트 통과 (7개)
- [x] 401 응답이 유효하지 않은 요청에 대해 반환됨
- [x] X-User-Id 헤더 정상 전달 (JWT subject 클레임)
- [x] X-User-Roles 헤더 정상 전달 (JWT roles 클레임, 쉼표로 구분)
- [x] 공개 경로(/api/public/**) 인증 없이 접근 가능
