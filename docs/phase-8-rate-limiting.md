# Phase 8: Rate Limiting (Optional)

## 목표
Redis 기반 Rate Limiting 구현

## PRD 요구사항
- 초당 10 요청
- 버스트 20 요청
- Redis 기반 토큰 버킷

## 테스트 목록

### 8.1 초당 10 요청 초과 시 429를 반환한다
```kotlin
@Test
fun `초당 10 요청 초과 시 429 반환`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    // 10개 요청 성공
    repeat(10) {
        webTestClient.get().uri("/api/users/1")
            .header("Authorization", "Bearer $validToken")
            .exchange()
            .expectStatus().isOk
    }

    // 11번째 요청은 429
    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isEqualTo(429)
}
```

### 8.2 버스트 20 요청까지 허용된다
```kotlin
@Test
fun `버스트 20 요청까지 허용된다`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    // 버스트로 20개까지 허용
    val results = (1..20).map {
        webTestClient.get().uri("/api/users/1")
            .header("Authorization", "Bearer $validToken")
            .exchange()
            .returnResult(Void::class.java)
            .status
    }

    val successCount = results.count { it.is2xxSuccessful }
    assertThat(successCount).isEqualTo(20)

    // 21번째는 거부
    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isEqualTo(429)
}
```

### 8.3 Rate Limit 헤더가 응답에 포함된다
```kotlin
@Test
fun `Rate Limit 헤더가 응답에 포함된다`() {
    stubFor(get(urlPathEqualTo("/users/1")).willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("Authorization", "Bearer $validToken")
        .exchange()
        .expectStatus().isOk
        .expectHeader().exists("X-RateLimit-Remaining")
        .expectHeader().exists("X-RateLimit-Limit")
        .expectHeader().exists("X-RateLimit-Reset")
}
```

## 구현 가이드

### 의존성 추가 (build.gradle.kts)
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
}
```

### application.yml
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@userKeyResolver}"
```

### RateLimiterConfig.kt
```kotlin
@Configuration
class RateLimiterConfig {

    @Bean
    fun userKeyResolver(): KeyResolver {
        return KeyResolver { exchange ->
            // JWT에서 사용자 ID 추출, 없으면 IP 사용
            val userId = exchange.request.headers.getFirst("X-User-Id")
            val clientIp = exchange.request.remoteAddress?.address?.hostAddress ?: "anonymous"

            Mono.just(userId ?: clientIp)
        }
    }

    @Bean
    fun rateLimiterHeaderFilter(): GlobalFilter {
        return GlobalFilter { exchange, chain ->
            chain.filter(exchange).then(Mono.fromRunnable {
                val response = exchange.response
                val remaining = exchange.attributes["rateLimitRemaining"] as? Long ?: -1
                val limit = exchange.attributes["rateLimitLimit"] as? Long ?: -1
                val reset = exchange.attributes["rateLimitReset"] as? Long ?: -1

                if (remaining >= 0) {
                    response.headers.add("X-RateLimit-Remaining", remaining.toString())
                    response.headers.add("X-RateLimit-Limit", limit.toString())
                    response.headers.add("X-RateLimit-Reset", reset.toString())
                }
            })
        }
    }
}
```

### 커스텀 Rate Limiter (고급)
```kotlin
@Component
class CustomRateLimiter(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) {
    companion object {
        const val RATE_LIMIT = 10
        const val BURST_CAPACITY = 20
        const val WINDOW_SECONDS = 1L
    }

    fun isAllowed(key: String): Mono<RateLimitResult> {
        val script = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            local remaining = limit - current
            if remaining < 0 then remaining = 0 end
            return {current <= limit and 1 or 0, remaining, redis.call('TTL', key)}
        """.trimIndent()

        return redisTemplate.execute(
            RedisScript.of(script, List::class.java),
            listOf(key),
            listOf(BURST_CAPACITY.toString(), WINDOW_SECONDS.toString())
        ).map { result ->
            val list = result as List<*>
            RateLimitResult(
                allowed = (list[0] as Long) == 1L,
                remaining = list[1] as Long,
                resetIn = list[2] as Long
            )
        }.next()
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Long,
    val resetIn: Long
)
```

### 테스트용 Embedded Redis
```kotlin
@TestConfiguration
class EmbeddedRedisConfig {
    private lateinit var redisServer: RedisServer

    @PostConstruct
    fun startRedis() {
        redisServer = RedisServer(6379)
        redisServer.start()
    }

    @PreDestroy
    fun stopRedis() {
        redisServer.stop()
    }
}
```

## 완료 조건
- [ ] 모든 Rate Limiting 테스트 통과
- [ ] Redis 연동 정상 동작
- [ ] Rate Limit 헤더 응답 포함
- [ ] 429 응답 시 적절한 에러 메시지
