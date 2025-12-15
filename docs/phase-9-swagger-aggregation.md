# Phase 9: Swagger Aggregation

## 목표
하위 백엔드 서비스들의 Swagger 문서를 Gateway에서 통합하여 제공

## PRD 요구사항
- Swagger UI 제공 (/swagger-ui.html)
- Gateway의 OpenAPI 스펙 제공 (/v3/api-docs)
- 각 백엔드 서비스의 api-docs를 Gateway를 통해 프록시
- Swagger UI에서 드롭다운으로 서비스 선택 가능
- 서비스별 swagger-enabled 설정으로 노출 제어
- gateway.services 설정 기반 동적 URL 생성

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                     API Gateway (Acadia)                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Swagger UI (/swagger-ui.html)          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │   │
│  │  │user-service │  │order-service│  │ service-N  │  │   │
│  │  │   (dropdown)│  │  (dropdown) │  │ (dropdown) │  │   │
│  │  └─────────────┘  └─────────────┘  └────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│                            │                                │
│  Routes: /v3/api-docs/{service-name}/**                    │
└─────────────────────────────────────────────────────────────┘
         │                        │                     │
         ▼                        ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  user-service   │  │  order-service  │  │    service-N    │
│ /v3/api-docs    │  │  /v3/api-docs   │  │  /v3/api-docs   │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

## 테스트 목록

### 9.1 /swagger-ui.html 엔드포인트가 Swagger UI를 반환한다
```kotlin
@Test
fun `swagger-ui html 엔드포인트가 Swagger UI를 반환한다`() {
    webTestClient
        .get()
        .uri("/swagger-ui.html")
        .exchange()
        .expectStatus()
        .is3xxRedirection
        .expectHeader()
        .location("/swagger-ui/index.html")
}
```

### 9.2 /v3/api-docs 엔드포인트가 Gateway의 OpenAPI 스펙을 반환한다
```kotlin
@Test
fun `v3 api-docs 엔드포인트가 OpenAPI 스펙을 반환한다`() {
    webTestClient
        .get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi").exists()
        .jsonPath("$.info").exists()
}
```

### 9.3 등록된 서비스의 api-docs가 Gateway를 통해 프록시된다
```kotlin
@Test
fun `등록된 서비스의 api-docs가 Gateway를 통해 프록시된다`() {
    // Given: user-service의 /v3/api-docs를 모킹
    wireMock.stubFor(
        get(urlPathMatching("/v3/api-docs"))
            .willReturn(ok().withBody("""{"openapi": "3.0.0"}"""))
    )

    // When & Then: Gateway를 통해 접근 가능
    webTestClient
        .get()
        .uri("/v3/api-docs/user-service")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.openapi").isEqualTo("3.0.0")
}
```

### 9.4 Swagger UI 드롭다운에서 각 서비스를 선택할 수 있다
```kotlin
@Test
fun `Swagger UI 설정에서 서비스 URL 목록이 반환된다`() {
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls").isArray
        .jsonPath("$.urls[*].name").exists()
        .jsonPath("$.urls[*].url").exists()
}
```

### 9.5 서비스 설정에서 swagger-enabled: false인 서비스는 목록에서 제외된다
```kotlin
@Test
fun `swagger-enabled false인 서비스는 Swagger 목록에서 제외된다`() {
    // Given: order-service는 swagger-enabled: false로 설정됨
    // When & Then: swagger-config에서 order-service가 제외됨
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls[?(@.name=='order-service')]").doesNotExist()
}
```

### 9.6 swagger-ui.urls가 gateway.services 기반으로 동적 생성된다
```kotlin
@Test
fun `swagger-ui urls가 gateway services 기반으로 동적 생성된다`() {
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls[?(@.name=='user-service')].url")
        .isEqualTo("/v3/api-docs/user-service")
}
```

## 구현

### 의존성 (build.gradle.kts)
```kotlin
// Spring Boot 4.x에는 springdoc v3.0.0 필요
implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.0")
```

### GatewayProperties.kt (서비스 설정 확장)
```kotlin
@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    val services: List<ServiceConfig> = emptyList(),
    val retry: RetryConfig = RetryConfig(),
) {
    data class ServiceConfig(
        val name: String = "",
        val path: String = "",
        val url: String = "",
        val stripPrefix: Int = 1,
        val hasPublicPath: Boolean = false,
        val swaggerEnabled: Boolean = true,  // 추가
    )
}
```

### SwaggerConfig.kt (동적 URL 생성)
```kotlin
@Configuration
class SwaggerConfig(
    private val gatewayProperties: GatewayProperties,
) {
    @Bean
    fun swaggerUiConfigParameters(): SwaggerUiConfigParameters {
        return SwaggerUiConfigParameters().apply {
            gatewayProperties.services
                .filter { it.swaggerEnabled }
                .forEach { service ->
                    urls.add(SwaggerUrl(service.name, "/v3/api-docs/${service.name}"))
                }
        }
    }
}
```

### GatewayConfig.kt (api-docs 라우팅 추가)
```kotlin
// 각 서비스의 /v3/api-docs 프록시 라우트 추가
props.services
    .filter { it.swaggerEnabled }
    .forEach { service ->
        routes = routes.route("${service.name}-api-docs") { r ->
            r.path("/v3/api-docs/${service.name}")
             .filters { f ->
                 f.rewritePath("/v3/api-docs/${service.name}", "/v3/api-docs")
             }
             .uri(service.url)
        }
    }
```

### application.yml
```yaml
gateway:
  services:
    - name: user-service
      path: /api/users/**
      url: http://localhost:8081
      strip-prefix: 1
      has-public-path: true
      swagger-enabled: true
    - name: order-service
      path: /api/orders/**
      url: http://localhost:8082
      strip-prefix: 1
      has-public-path: false
      swagger-enabled: false  # Swagger에서 제외

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    enabled: true
```

## 백엔드 서비스 요구사항

각 백엔드 서비스에서 Swagger 문서가 노출되어야 Gateway에서 통합 가능:

```yaml
# 백엔드 서비스의 application.yml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
```

## Swagger UI 접근 경로

| 경로 | 설명 |
|------|------|
| `/swagger-ui.html` | Swagger UI (리다이렉트) |
| `/swagger-ui/index.html` | Swagger UI (실제 페이지) |
| `/v3/api-docs` | Gateway OpenAPI 스펙 |
| `/v3/api-docs/{service-name}` | 개별 서비스 OpenAPI 스펙 |
| `/v3/api-docs/swagger-config` | Swagger UI 설정 (URL 목록) |

## 완료 조건
- [x] 9.1 /swagger-ui.html 엔드포인트가 Swagger UI를 반환한다
- [ ] 9.2 /v3/api-docs 엔드포인트가 Gateway의 OpenAPI 스펙을 반환한다
- [ ] 9.3 등록된 서비스의 api-docs가 Gateway를 통해 프록시된다
- [ ] 9.4 Swagger UI 드롭다운에서 각 서비스를 선택할 수 있다
- [ ] 9.5 swagger-enabled: false인 서비스는 목록에서 제외된다
- [ ] 9.6 swagger-ui.urls가 gateway.services 기반으로 동적 생성된다
