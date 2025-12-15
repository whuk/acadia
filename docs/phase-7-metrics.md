# Phase 7: Prometheus 메트릭

## 목표
운영 모니터링을 위한 메트릭 수집 및 노출

## PRD 요구사항
- Prometheus 메트릭 노출
- 요청 수, 응답 시간, Circuit Breaker 상태

## 테스트 목록

### 7.1 /actuator/prometheus 엔드포인트가 메트릭을 반환한다
```kotlin
@Test
fun `actuator prometheus 엔드포인트가 메트릭을 반환한다`() {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk
        .expectHeader()
        .contentType("text/plain;version=0.0.4;charset=utf-8")
}
```

### 7.2 요청 수 메트릭이 기록된다
```kotlin
@Test
fun `요청 수 메트릭이 기록된다`() {
    // Given: actuator 엔드포인트에 요청
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk

    // When: prometheus 메트릭 조회
    val metricsResponse =
        webTestClient
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

    // Then: HTTP 요청 수 메트릭이 존재
    assertThat(metricsResponse).contains("http_server_requests_seconds_count")
}
```

### 7.3 응답 시간 메트릭이 기록된다
```kotlin
@Test
fun `응답 시간 메트릭이 기록된다`() {
    // Given: actuator 엔드포인트에 요청
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk

    // When: prometheus 메트릭 조회
    val metricsResponse =
        webTestClient
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

    // Then: HTTP 응답 시간 메트릭이 존재
    assertThat(metricsResponse).contains("http_server_requests_seconds_sum")
}
```

### 7.4 Circuit Breaker 상태 메트릭이 기록된다
```kotlin
@Test
fun `Circuit Breaker 상태 메트릭이 기록된다`() {
    // When: prometheus 메트릭 조회
    val metricsResponse =
        webTestClient
            .get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

    // Then: Circuit Breaker 상태 메트릭이 존재
    assertThat(metricsResponse).contains("resilience4j_circuitbreaker_state")
}
```

## 구현 가이드

### 의존성 추가 (build.gradle.kts)
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

### application.yml
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,gateway,prometheus
  endpoint:
    health:
      show-details: always
```

## 주요 메트릭 목록

| 메트릭 이름 | 설명 |
|------------|------|
| `http_server_requests_seconds_count` | 총 요청 수 |
| `http_server_requests_seconds_sum` | 총 응답 시간 |
| `http_server_requests_seconds_max` | 최대 응답 시간 |
| `resilience4j_circuitbreaker_state` | CB 상태 |
| `resilience4j_circuitbreaker_calls_total` | CB 호출 수 |
| `jvm_memory_used_bytes` | JVM 메모리 사용량 |
| `system_cpu_usage` | CPU 사용률 |

## 완료 조건
- [x] 모든 메트릭 테스트 통과
- [x] Prometheus 스크래핑 가능
- [x] 주요 메트릭 수집 확인
- [x] Grafana 대시보드 연동 가능
