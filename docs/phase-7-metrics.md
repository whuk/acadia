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
fun `prometheus 엔드포인트가 메트릭을 반환한다`() {
    webTestClient.get().uri("/actuator/prometheus")
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
        .expectBody(String::class.java)
        .value { body ->
            assertThat(body).contains("jvm_memory_used_bytes")
        }
}
```

### 7.2 요청 수 메트릭이 기록된다
```kotlin
@Test
fun `요청 수 메트릭이 기록된다`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    // 요청 3회 수행
    repeat(3) {
        webTestClient.get().uri("/api/users/1")
            .header("Authorization", "Bearer $validToken")
            .exchange()
    }

    webTestClient.get().uri("/actuator/prometheus")
        .exchange()
        .expectBody(String::class.java)
        .value { body ->
            assertThat(body).contains("http_server_requests_seconds_count")
            assertThat(body).contains("uri=\"/api/users/{id}\"")
        }
}
```

### 7.3 응답 시간 메트릭이 기록된다
```kotlin
@Test
fun `응답 시간 메트릭이 기록된다`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .willReturn(ok().withFixedDelay(100)))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()

    webTestClient.get().uri("/actuator/prometheus")
        .exchange()
        .expectBody(String::class.java)
        .value { body ->
            assertThat(body).contains("http_server_requests_seconds_sum")
            assertThat(body).contains("http_server_requests_seconds_max")
        }
}
```

### 7.4 Circuit Breaker 상태 메트릭이 기록된다
```kotlin
@Test
fun `Circuit Breaker 상태 메트릭이 기록된다`() {
    // Circuit Breaker 동작 트리거
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(serverError()))

    repeat(10) {
        webTestClient.get().uri("/api/users/1")
            .header("Authorization", "Bearer $validToken")
            .exchange()
    }

    webTestClient.get().uri("/actuator/prometheus")
        .exchange()
        .expectBody(String::class.java)
        .value { body ->
            assertThat(body).contains("resilience4j_circuitbreaker_state")
            assertThat(body).contains("name=\"user-service\"")
        }
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
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: always
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
```

### MetricsConfig.kt
```kotlin
@Configuration
class MetricsConfig {

    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config().commonTags(
                "application", "acadia",
                "environment", System.getenv("ENV") ?: "local"
            )
        }
    }
}
```

### 커스텀 메트릭 추가 (Optional)
```kotlin
@Component
class GatewayMetrics(private val meterRegistry: MeterRegistry) {

    private val routingCounter = Counter.builder("gateway.routing.total")
        .description("Total routing requests")
        .register(meterRegistry)

    private val routingTimer = Timer.builder("gateway.routing.duration")
        .description("Routing duration")
        .register(meterRegistry)

    fun recordRouting(routeId: String, duration: Duration) {
        routingCounter.increment()
        routingTimer.record(duration)
    }
}
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
- [ ] 모든 메트릭 테스트 통과
- [ ] Prometheus 스크래핑 가능
- [ ] 주요 메트릭 수집 확인
- [ ] Grafana 대시보드 연동 가능
