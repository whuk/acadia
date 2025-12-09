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
    webTestClient.options().uri("/api/users/1")
        .header("Origin", "https://example.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus().isOk
        .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://example.com")
}
```

### 6.2 허용되지 않은 Origin은 CORS 오류를 반환한다
```kotlin
@Test
fun `허용되지 않은 Origin은 CORS 헤더가 없다`() {
    webTestClient.options().uri("/api/users/1")
        .header("Origin", "https://malicious.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectHeader().doesNotExist("Access-Control-Allow-Origin")
}
```

### 6.3 허용된 HTTP 메서드만 CORS 응답에 포함된다
```kotlin
@Test
fun `허용된 메서드만 CORS 응답에 포함된다`() {
    webTestClient.options().uri("/api/users/1")
        .header("Origin", "https://example.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus().isOk
        .expectHeader().value("Access-Control-Allow-Methods") { methods ->
            assertThat(methods).contains("GET", "POST", "PUT", "DELETE")
            assertThat(methods).doesNotContain("PATCH", "TRACE")
        }
}
```

### 6.4 credentials가 허용된다
```kotlin
@Test
fun `credentials가 허용된다`() {
    webTestClient.options().uri("/api/users/1")
        .header("Origin", "https://example.com")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus().isOk
        .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
}
```

## 구현 가이드

### application.yml CORS 설정
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/api/**]':
            allowedOrigins:
              - "https://example.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
            allowedHeaders:
              - "*"
            allowCredentials: true
            maxAge: 3600
```

### CorsConfig.kt (프로그래밍 방식)
```kotlin
@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("https://example.com")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }
}
```

### SecurityConfig.kt 업데이트
```kotlin
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val corsConfigurationSource: CorsConfigurationSource
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { it.disable() }
            .headers { headers ->
                headers
                    .frameOptions { it.deny() }
                    .contentSecurityPolicy { it.policyDirectives("default-src 'self'") }
                    .xssProtection { }
            }
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

## 완료 조건
- [ ] 모든 CORS 테스트 통과
- [ ] 허용된 Origin만 접근 가능
- [ ] credentials 정상 동작
- [ ] 보안 헤더 설정 완료
