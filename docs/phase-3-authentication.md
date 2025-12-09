# Phase 3: 인증 (JWT)

## 목표
JWT 기반 인증 구현 및 사용자 정보 헤더 전달

## PRD 요구사항
- Authorization: Bearer JWT 검증
- 유효성 검증 실패 시 401
- X-User-Id, X-User-Roles 헤더로 사용자 정보 전달

## 테스트 목록

### 3.1 Authorization 헤더 없는 요청은 401을 반환한다
```kotlin
@Test
fun `Authorization 헤더 없으면 401 반환`() {
    webTestClient.get().uri("/api/users/1")
        .exchange()
        .expectStatus().isUnauthorized
}
```

### 3.2 잘못된 JWT 토큰은 401을 반환한다
```kotlin
@Test
fun `잘못된 형식의 JWT는 401 반환`() {
    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer invalid.token.here")
        .exchange()
        .expectStatus().isUnauthorized
}
```

### 3.3 만료된 JWT 토큰은 401을 반환한다
```kotlin
@Test
fun `만료된 JWT는 401 반환`() {
    val expiredToken = createExpiredJwt()

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $expiredToken")
        .exchange()
        .expectStatus().isUnauthorized
}
```

### 3.4 유효한 JWT 토큰은 라우팅이 진행된다
```kotlin
@Test
fun `유효한 JWT로 요청하면 라우팅 성공`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))
    val validToken = createValidJwt(userId = "user-123")

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk
}
```

### 3.5 JWT에서 추출한 사용자 ID가 X-User-Id 헤더로 전달된다
```kotlin
@Test
fun `JWT의 sub 클레임이 X-User-Id 헤더로 전달된다`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .withHeader("X-User-Id", equalTo("user-123"))
        .willReturn(ok()))

    val token = createValidJwt(userId = "user-123")

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $token")
        .exchange()
        .expectStatus().isOk
}
```

### 3.6 JWT에서 추출한 역할이 X-User-Roles 헤더로 전달된다
```kotlin
@Test
fun `JWT의 roles 클레임이 X-User-Roles 헤더로 전달된다`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .withHeader("X-User-Roles", equalTo("ADMIN,USER"))
        .willReturn(ok()))

    val token = createValidJwt(
        userId = "user-123",
        roles = listOf("ADMIN", "USER")
    )

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $token")
        .exchange()
        .expectStatus().isOk
}
```

### 3.7 공개 경로(/api/public/**)는 인증 없이 접근 가능하다
```kotlin
@Test
fun `public 경로는 인증 없이 접근 가능`() {
    stubFor(get(urlPathMatching("/public/.*")).willReturn(ok()))

    webTestClient.get().uri("/api/public/health")
        .exchange()
        .expectStatus().isOk
}
```

## 구현 가이드

### JWT 설정 (application.yml)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          # 또는 jwk-set-uri: https://auth.example.com/.well-known/jwks.json
```

### SecurityConfig.kt
```kotlin
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/public/**").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
            .build()
    }
}
```

### JwtToHeaderFilter.kt
```kotlin
@Component
class JwtToHeaderFilter : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val principal = exchange.getPrincipal<Jwt>()

        return principal.flatMap { jwt ->
            val mutatedRequest = exchange.request.mutate()
                .header("X-User-Id", jwt.subject)
                .header("X-User-Roles", jwt.getClaimAsStringList("roles")?.joinToString(",") ?: "")
                .build()

            chain.filter(exchange.mutate().request(mutatedRequest).build())
        }.switchIfEmpty(chain.filter(exchange))
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1
}
```

## 테스트 유틸리티

### JwtTestUtil.kt
```kotlin
object JwtTestUtil {
    private val key = Keys.secretKeyFor(SignatureAlgorithm.HS256)

    fun createValidJwt(
        userId: String,
        roles: List<String> = emptyList(),
        expiresIn: Duration = Duration.ofHours(1)
    ): String {
        return Jwts.builder()
            .setSubject(userId)
            .claim("roles", roles)
            .setIssuedAt(Date())
            .setExpiration(Date.from(Instant.now().plus(expiresIn)))
            .signWith(key)
            .compact()
    }

    fun createExpiredJwt(): String {
        return Jwts.builder()
            .setSubject("expired-user")
            .setExpiration(Date.from(Instant.now().minus(Duration.ofHours(1))))
            .signWith(key)
            .compact()
    }
}
```

## 완료 조건
- [ ] 모든 인증 테스트 통과
- [ ] 401 응답이 적절한 에러 메시지 포함
- [ ] X-User-Id, X-User-Roles 헤더 정상 전달
