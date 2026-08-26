# Phase 4: 장애 대응 (Resilience)

## 목표
Timeout, Retry, Circuit Breaker를 통한 장애 대응 구현

## PRD 요구사항
- 전체 요청 타임아웃: 3초
- 백엔드 연결 타임아웃: 1초
- 재시도: 3회
- Circuit Breaker: 실패율 50% → OPEN

## 테스트 목록

### 4.1 백엔드 응답이 3초 초과 시 504를 반환한다
```kotlin
@Test
fun `백엔드 응답이 3초 초과하면 504 반환`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .willReturn(ok().withFixedDelay(4000)))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isEqualTo(504)
}
```

### 4.2 백엔드 연결이 1초 초과 시 504를 반환한다
```kotlin
@Test
fun `백엔드 연결 실패 시 504 반환`() {
    // 연결 불가능한 포트로 설정
    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isEqualTo(504)
}
```

### 4.3 백엔드 실패 시 최대 3회 재시도한다
```kotlin
@Test
fun `500 오류 시 3회 재시도 후 성공`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .inScenario("retry")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(serverError())
        .willSetStateTo("first-failure"))

    stubFor(get(urlPathEqualTo("/users/1"))
        .inScenario("retry")
        .whenScenarioStateIs("first-failure")
        .willReturn(serverError())
        .willSetStateTo("second-failure"))

    stubFor(get(urlPathEqualTo("/users/1"))
        .inScenario("retry")
        .whenScenarioStateIs("second-failure")
        .willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk

    verify(3, getRequestedFor(urlPathEqualTo("/users/1")))
}
```

### 4.4 실패율 50% 초과 시 Circuit Breaker가 열린다
```kotlin
@Test
fun `실패율 50% 초과 시 Circuit Breaker OPEN`() {
    // 10개 요청 중 6개 실패 시뮬레이션
    stubFor(get(urlPathEqualTo("/users/1"))
        .willReturn(serverError()))

    repeat(10) {
        webTestClient.get().uri("/api/users/1")
            .header("Authorization", "Bearer $validToken")
            .exchange()
    }

    // Circuit Breaker가 열린 상태 확인
    val cbState = circuitBreakerRegistry.circuitBreaker("user-service").state
    assertThat(cbState).isEqualTo(CircuitBreaker.State.OPEN)
}
```

### 4.5 Circuit Breaker 열린 상태에서 503을 반환한다
```kotlin
@Test
fun `Circuit Breaker OPEN 상태에서 503 반환`() {
    // Circuit Breaker를 강제로 OPEN
    circuitBreakerRegistry.circuitBreaker("user-service")
        .transitionToOpenState()

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isEqualTo(503)
        .expectBody()
        .jsonPath("$.error").isEqualTo("Service Unavailable")
}
```

### 4.6 백엔드 5xx 오류는 502로 변환된다
```kotlin
@Test
fun `백엔드 500 오류는 502로 변환`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .willReturn(serverError().withBody("Internal Error")))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isEqualTo(502)
}
```

## 구현 가이드

### 의존성 추가 (build.gradle.kts)
```kotlin
dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")
}
```

### application.yml
```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: Retry
          args:
            retries: 3
            statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
            methods: GET
            backoff:
              firstBackoff: 100ms
              maxBackoff: 500ms
              factor: 2

      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: user-service
                fallbackUri: forward:/fallback/user-service

      httpclient:
        connect-timeout: 1000
        response-timeout: 3s

resilience4j:
  circuitbreaker:
    # 현재 구현: 서비스별 인스턴스(cb-{service.name})가 이 기본 설정으로 생성된다.
    # 장애가 서비스 단위로 격리되며, 서비스 추가 시 resilience4j 설정이 필요 없다.
    configs:
      default:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 3
```

### FallbackController.kt
```kotlin
@RestController
@RequestMapping("/fallback")
class FallbackController {

    @GetMapping("/{serviceName}")
    fun fallback(@PathVariable serviceName: String): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                error = "Service Unavailable",
                message = "$serviceName is currently unavailable",
                timestamp = Instant.now()
            ))
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Instant
)
```

### GlobalErrorHandler.kt
```kotlin
@Component
class GlobalErrorHandler : ErrorWebExceptionHandler {

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response

        val status = when (ex) {
            is TimeoutException -> HttpStatus.GATEWAY_TIMEOUT
            is ConnectException -> HttpStatus.BAD_GATEWAY
            is WebClientResponseException.ServiceUnavailable -> HttpStatus.SERVICE_UNAVAILABLE
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        response.statusCode = status
        // ... JSON 응답 작성
    }
}
```

## 완료 조건
- [x] 모든 Resilience 테스트 통과
- [x] Circuit Breaker 상태 전이 정상 동작
- [x] 타임아웃 및 재시도 정상 동작
- [x] 에러 응답 형식 일관성 유지
