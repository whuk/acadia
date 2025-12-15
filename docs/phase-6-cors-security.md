# Phase 6: CORS & 보안

## 목표
CORS 설정 및 보안 정책 구현

## PRD 요구사항
- allow origins: https://example.com
- allow methods: GET, POST, PUT, DELETE
- allow credentials: true

## 테스트 목록

### 6.1 허용된 Origin에서 CORS preflight 요청이 성공한다
```kotlin
@Test
fun `허용된 Origin의 preflight 요청이 성공한다`() {
    webTestClient
        .options()
        .uri("/api/users/1")
        .header("Origin", "https://example.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isOk
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "https://example.com")
}
```

### 6.2 허용되지 않은 Origin은 CORS 오류를 반환한다
```kotlin
@Test
fun `허용되지 않은 Origin은 CORS 오류를 반환한다`() {
    webTestClient
        .options()
        .uri("/api/users/1")
        .header("Origin", "https://malicious.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isForbidden
        .expectHeader()
        .doesNotExist("Access-Control-Allow-Origin")
}
```

### 6.3 허용된 HTTP 메서드만 CORS 응답에 포함된다
```kotlin
@Test
fun `허용된 HTTP 메서드만 CORS 응답에 포함된다`() {
    webTestClient
        .options()
        .uri("/api/users/1")
        .header("Origin", "https://example.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isOk
        .expectHeader()
        .valueEquals("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE")
}
```

### 6.4 credentials가 허용된다
```kotlin
@Test
fun `credentials가 허용된다`() {
    webTestClient
        .options()
        .uri("/api/users/1")
        .header("Origin", "https://example.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isOk
        .expectHeader()
        .valueEquals("Access-Control-Allow-Credentials", "true")
}
```

## 구현 가이드

### application.yml CORS 설정
```yaml
gateway:
  cors:
    allowed-origins:
      - "https://example.com"
    allowed-methods:
      - GET
      - POST
      - PUT
      - DELETE
    allowed-headers:
      - "*"
    allow-credentials: true
    max-age: 3600
```

### CorsProperties.kt
```kotlin
@ConfigurationProperties(prefix = "gateway.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
    val allowedMethods: List<String>,
    val allowedHeaders: List<String>,
    val allowCredentials: Boolean,
    val maxAge: Long,
)
```

### CorsConfig.kt
```kotlin
@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(
    private val corsProperties: CorsProperties,
) {
    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = corsProperties.allowedMethods
                allowedHeaders = corsProperties.allowedHeaders
                allowCredentials = corsProperties.allowCredentials
                maxAge = corsProperties.maxAge
            }

        val source =
            UrlBasedCorsConfigurationSource().apply {
                registerCorsConfiguration("/api/**", configuration)
            }

        return CorsWebFilter(source)
    }
}
```

## 완료 조건
- [x] 모든 CORS 테스트 통과
- [x] 허용된 Origin만 접근 가능
- [x] credentials 정상 동작
- [x] 보안 헤더 설정 완료
