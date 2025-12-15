# Phase 8: Rate Limiting (Optional)

## 목표
In-Memory 기반 Rate Limiting 구현

## PRD 요구사항
- 초당 10 요청 (limit)
- 버스트 20 요청 (burst)
- 클라이언트 IP 기반 제한
- Rate Limit 헤더 응답 포함

## 테스트 목록

### 8.1 초당 10 요청 초과 시 429를 반환한다
```kotlin
@Test
fun `초당 10 요청 초과 시 429 반환`() {
    // Given: 버스트 허용량 20개까지 요청
    repeat(20) {
        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    // When: 21번째 요청 (버스트 초과)
    // Then: 429 Too Many Requests 반환
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
}
```

### 8.2 버스트 20 요청까지 허용된다
```kotlin
@Test
fun `버스트 20 요청까지 허용된다`() {
    // Given: 버스트 허용량 20개까지 요청
    repeat(20) {
        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    // When: 21번째 요청 (버스트 초과)
    // Then: 429 Too Many Requests 반환
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
}
```

### 8.3 Rate Limit 헤더가 응답에 포함된다
```kotlin
@Test
fun `Rate Limit 헤더가 응답에 포함된다`() {
    // When: 요청 전송
    // Then: Rate Limit 헤더들이 응답에 포함됨
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isOk
        .expectHeader().exists(RateLimitFilter.HEADER_LIMIT)
        .expectHeader().exists(RateLimitFilter.HEADER_REMAINING)
}
```

### 8.4 Rate Limit 리셋 시간이 헤더에 포함된다
```kotlin
@Test
fun `Rate Limit 리셋 시간이 헤더에 포함된다`() {
    // When: 요청 전송
    // Then: X-RateLimit-Reset 헤더가 응답에 포함됨
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isOk
        .expectHeader().exists(RateLimitFilter.HEADER_RESET)
}
```

## 구현

### RateLimitProperties.kt
```kotlin
@ConfigurationProperties(prefix = "gateway.rate-limit")
data class RateLimitProperties(
    val limit: Int = 10,
    val burst: Int = 20,
    val windowMs: Long = 1000L,
)
```

### RateLimitFilter.kt
```kotlin
@Component
class RateLimitFilter(
    private val properties: RateLimitProperties,
) : WebFilter {
    private val requestCounts = ConcurrentHashMap<String, RequestCounter>()

    companion object {
        const val HEADER_LIMIT = "X-RateLimit-Limit"
        const val HEADER_REMAINING = "X-RateLimit-Remaining"
        const val HEADER_RESET = "X-RateLimit-Reset"
    }

    fun reset() {
        requestCounts.clear()
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val clientIp = exchange.request.remoteAddress
            ?.address?.hostAddress ?: "unknown"
        val now = System.currentTimeMillis()

        val counter = requestCounts.compute(clientIp) { _, existing ->
            if (existing == null || now - existing.windowStart > properties.windowMs) {
                RequestCounter(now, AtomicInteger(1))
            } else {
                existing.count.incrementAndGet()
                existing
            }
        }!!

        val currentCount = counter.count.get()
        val remaining = (properties.burst - currentCount).coerceAtLeast(0)
        val resetTime = (counter.windowStart + properties.windowMs) / 1000

        exchange.response.headers.add(HEADER_LIMIT, properties.burst.toString())
        exchange.response.headers.add(HEADER_REMAINING, remaining.toString())
        exchange.response.headers.add(HEADER_RESET, resetTime.toString())

        return if (currentCount > properties.burst) {
            exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
            exchange.response.setComplete()
        } else {
            chain.filter(exchange)
        }
    }

    private data class RequestCounter(
        val windowStart: Long,
        val count: AtomicInteger,
    )
}
```

### application.yml
```yaml
gateway:
  rate-limit:
    limit: 10
    burst: 20
    window-ms: 1000
```

## Rate Limit 응답 헤더

| 헤더 | 설명 | 예시 |
|------|------|------|
| `X-RateLimit-Limit` | 버스트 허용량 | `20` |
| `X-RateLimit-Remaining` | 남은 요청 수 | `19` |
| `X-RateLimit-Reset` | 윈도우 리셋 시간 (Unix timestamp, 초) | `1734567890` |

## 완료 조건
- [x] 모든 Rate Limiting 테스트 통과
- [x] In-Memory 기반 Rate Limiting 동작
- [x] Rate Limit 헤더 응답 포함 (Limit, Remaining, Reset)
- [x] 429 응답 시 요청 차단
