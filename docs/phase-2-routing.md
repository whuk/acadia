# Phase 2: 기본 라우팅

## 목표
서비스별 라우팅 규칙 구현 및 요청 전달 검증

## PRD 요구사항
- /api/users/** → user-service
- /api/orders/** → order-service
- 원본 메서드, 헤더, 바디 유지

## 테스트 목록

### 2.1 /api/users/** 요청이 user-service로 라우팅된다 ✅
```kotlin
@Test
fun `users 경로가 user-service로 라우팅된다`() {
    wireMock.stubFor(
        get(urlPathMatching("/users/.*"))
            .willReturn(ok().withBody("""{"id": 1}"""))
    )

    webTestClient.get().uri("/api/users/1")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(1)
}
```

### 2.2 /api/orders/** 요청이 order-service로 라우팅된다
```kotlin
@Test
fun `orders 경로가 order-service로 라우팅된다`() {
    stubFor(get(urlPathMatching("/orders/.*"))
        .willReturn(ok().withBody("""{"orderId": "ORD-001"}""")))

    webTestClient.get().uri("/api/orders/ORD-001")
        .exchange()
        .expectStatus().isOk
}
```

### 2.3 정의되지 않은 경로는 404를 반환한다
```kotlin
@Test
fun `정의되지 않은 경로는 404를 반환한다`() {
    webTestClient.get().uri("/api/unknown/path")
        .exchange()
        .expectStatus().isNotFound
}
```

### 2.4 라우팅 시 원본 HTTP 메서드가 유지된다
```kotlin
@Test
fun `POST 요청이 POST로 전달된다`() {
    stubFor(post(urlPathEqualTo("/users"))
        .willReturn(created()))

    webTestClient.post().uri("/api/users")
        .bodyValue("""{"name": "test"}""")
        .exchange()
        .expectStatus().isCreated
}
```

### 2.5 라우팅 시 원본 헤더가 전달된다
```kotlin
@Test
fun `커스텀 헤더가 백엔드로 전달된다`() {
    stubFor(get(urlPathEqualTo("/users/1"))
        .withHeader("X-Custom-Header", equalTo("custom-value"))
        .willReturn(ok()))

    webTestClient.get().uri("/api/users/1")
        .header("X-Custom-Header", "custom-value")
        .exchange()
        .expectStatus().isOk
}
```

### 2.6 라우팅 시 원본 바디가 전달된다
```kotlin
@Test
fun `요청 바디가 백엔드로 전달된다`() {
    val requestBody = """{"name": "test", "email": "test@example.com"}"""

    stubFor(post(urlPathEqualTo("/users"))
        .withRequestBody(equalToJson(requestBody))
        .willReturn(created()))

    webTestClient.post().uri("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isCreated
}
```

## 구현 가이드

### 프로그래매틱 라우팅 설정 (GatewayConfig.kt)
```kotlin
@Configuration
class GatewayConfig {

    @Value("\${user-service.url:http://localhost:8081}")
    private lateinit var userServiceUrl: String

    @Bean
    fun customRouteLocator(builder: RouteLocatorBuilder): RouteLocator {
        return builder.routes()
            .route("user-service") { r ->
                r.path("/api/users/**")
                    .filters { f -> f.stripPrefix(1) }
                    .uri(userServiceUrl)
            }
            .build()
    }
}
```

### 테스트 설정 (WireMock JUnit 5 Extension)
```kotlin
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UserServiceRoutingTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension = WireMockExtension.newInstance().build()

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("user-service.url") { wireMock.baseUrl() }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient
}
```

### 의존성 (build.gradle.kts)
```kotlin
testImplementation("org.wiremock:wiremock-standalone:3.10.0")
```

## 완료 조건
- [x] 2.1 /api/users/** 라우팅 테스트 통과
- [ ] 모든 라우팅 테스트 통과
- [ ] 경로별 서비스 매핑 정상 동작
- [ ] 메서드/헤더/바디 무손실 전달
