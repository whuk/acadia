# Phase 5: 로깅 & 트레이싱

## 목표
구조화된 JSON 로깅 및 분산 추적 구현

## PRD 요구사항
- JSON 로그 형식
- X-Request-Id 생성 및 전달
- TraceId/SpanId 전달

## 테스트 목록

### 5.1 모든 요청에 X-Request-Id가 생성된다
```kotlin
@Test
fun `요청에 X-Request-Id가 생성되어 응답에 포함된다`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk
        .expectHeader().exists("X-Request-Id")
        .expectHeader().value("X-Request-Id") { requestId ->
            assertThat(requestId).matches("[a-f0-9\\-]{36}")
        }
}
```

### 5.2 클라이언트가 보낸 X-Request-Id가 있으면 유지된다
```kotlin
@Test
fun `클라이언트 X-Request-Id가 유지된다`() {
    val clientRequestId = "client-request-123"

    stubFor(get(urlPathEqualTo("/users/1"))
        .withHeader("X-Request-Id", equalTo(clientRequestId))
        .willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .header("X-Request-Id", clientRequestId)
        .exchange()
        .expectStatus().isOk
        .expectHeader().valueEquals("X-Request-Id", clientRequestId)
}
```

### 5.3 요청 로그가 JSON 형식으로 기록된다
```kotlin
@Test
fun `요청 로그가 JSON 형식으로 기록된다`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk

    // 로그 캡처 후 JSON 파싱 검증
    val logEntry = logCaptor.getLogs().find { it.contains("REQUEST") }
    assertThat(logEntry).isNotNull
    val json = objectMapper.readTree(logEntry)
    assertThat(json.has("timestamp")).isTrue
    assertThat(json.has("method")).isTrue
    assertThat(json.has("path")).isTrue
    assertThat(json.has("requestId")).isTrue
}
```

### 5.4 응답 로그가 JSON 형식으로 기록된다
```kotlin
@Test
fun `응답 로그가 JSON 형식으로 기록된다`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()

    val logEntry = logCaptor.getLogs().find { it.contains("RESPONSE") }
    assertThat(logEntry).isNotNull
    val json = objectMapper.readTree(logEntry)
    assertThat(json.has("status")).isTrue
    assertThat(json.has("duration")).isTrue
    assertThat(json.has("requestId")).isTrue
}
```

### 5.5 TraceId가 백엔드로 전달된다
```kotlin
@Test
fun `TraceId가 백엔드로 전달된다`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .withHeader("X-B3-TraceId", matching(".+"))
        .willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk

    verify(getRequestedFor(urlPathEqualTo("/users/1"))
        .withHeader("X-B3-TraceId", matching(".+")))
}
```

### 5.6 SpanId가 백엔드로 전달된다
```kotlin
@Test
fun `SpanId가 백엔드로 전달된다`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .withHeader("X-B3-SpanId", matching(".+"))
        .willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk

    verify(getRequestedFor(urlPathEqualTo("/users/1"))
        .withHeader("X-B3-SpanId", matching(".+")))
}
```

## 구현 가이드

### 의존성 추가 (build.gradle.kts)
```kotlin
dependencies {
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
}
```

### logback-spring.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

### application.yml
```yaml
management:
  tracing:
    sampling:
      probability: 1.0

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### RequestIdFilter.kt
```kotlin
@Component
class RequestIdFilter : GlobalFilter, Ordered {
    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val requestId = exchange.request.headers.getFirst(REQUEST_ID_HEADER)
            ?: UUID.randomUUID().toString()

        val mutatedRequest = exchange.request.mutate()
            .header(REQUEST_ID_HEADER, requestId)
            .build()

        val mutatedResponse = exchange.response.apply {
            headers.add(REQUEST_ID_HEADER, requestId)
        }

        MDC.put("requestId", requestId)

        return chain.filter(
            exchange.mutate()
                .request(mutatedRequest)
                .response(mutatedResponse)
                .build()
        ).doFinally { MDC.clear() }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
```

### LoggingFilter.kt
```kotlin
@Component
class LoggingFilter : GlobalFilter, Ordered {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val startTime = System.currentTimeMillis()
        val request = exchange.request

        logger.info("""{"type":"REQUEST","method":"${request.method}","path":"${request.path}","requestId":"${request.headers.getFirst("X-Request-Id")}"}""")

        return chain.filter(exchange).doFinally {
            val duration = System.currentTimeMillis() - startTime
            val status = exchange.response.statusCode?.value() ?: 0

            logger.info("""{"type":"RESPONSE","status":$status,"duration":${duration},"requestId":"${request.headers.getFirst("X-Request-Id")}"}""")
        }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10
}
```

## 완료 조건
- [x] 모든 로깅/트레이싱 테스트 통과
- [x] JSON 로그 형식 일관성
- [x] Request-Id 생성 및 전달 정상 동작
- [x] TraceId/SpanId 전파 정상 동작
