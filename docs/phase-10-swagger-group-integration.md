# Phase 10: Swagger 그룹 통합

## 목표
백엔드 서비스의 GroupedOpenApi 그룹을 Gateway Swagger UI에서 개별 선택 가능하도록 통합

## PRD 요구사항
- 백엔드 서비스의 swagger-config에서 그룹 목록 동적 수집
- Swagger UI 드롭다운에서 서비스/그룹 형식으로 선택 가능
- 그룹별 api-docs 라우팅 지원
- 동적 페칭 실패 시 정적 설정으로 폴백
- 기존 서비스 단위 동작과 하위 호환성 유지

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                     Gateway 시작                              │
│  1. 각 서비스의 /v3/api-docs/swagger-config 호출              │
│  2. 그룹 목록 수집 (API, Maintenance, Batch 등)              │
│  3. 서비스/그룹 조합 URL 생성                                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Swagger UI 드롭다운                              │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ staff-gpt/API    │  │ staff-gpt/Batch  │  ...           │
│  └──────────────────┘  └──────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Gateway 라우팅                                   │
│  /v3/api-docs/staff-gpt/Maintenance                         │
│         → localhost:8081/v3/api-docs/Maintenance            │
└─────────────────────────────────────────────────────────────┘
```

## 테스트 목록

### 10.1 SwaggerGroupFetcher가 서비스의 swagger-config에서 그룹 목록을 가져온다
```kotlin
@Test
fun `SwaggerGroupFetcher가 서비스의 swagger-config에서 그룹 목록을 가져온다`() {
    // Given: 백엔드 서비스가 swagger-config를 제공
    wireMock.stubFor(
        get(urlPathEqualTo("/v3/api-docs/swagger-config"))
            .willReturn(okJson("""
                {
                    "urls": [
                        {"url": "/v3/api-docs/API", "name": "API"},
                        {"url": "/v3/api-docs/Maintenance", "name": "Maintenance"}
                    ]
                }
            """))
    )

    // When: SwaggerGroupFetcher가 그룹 목록을 가져옴
    val groups = swaggerGroupFetcher.fetchGroups(wireMockUrl)

    // Then: 그룹 목록이 반환됨
    assertThat(groups).containsExactly("API", "Maintenance")
}
```

### 10.2 서비스 연결 실패 시 빈 목록을 반환한다
```kotlin
@Test
fun `서비스 연결 실패 시 빈 목록을 반환한다`() {
    // Given: 서비스가 응답하지 않음
    wireMock.stubFor(
        get(urlPathEqualTo("/v3/api-docs/swagger-config"))
            .willReturn(aResponse().withStatus(500))
    )

    // When: SwaggerGroupFetcher가 그룹 목록을 가져옴
    val groups = swaggerGroupFetcher.fetchGroups(wireMockUrl)

    // Then: 빈 목록이 반환됨
    assertThat(groups).isEmpty()
}
```

### 10.3 동적으로 가져온 그룹이 Swagger URL에 서비스/그룹 형식으로 추가된다
```kotlin
@Test
fun `동적으로 가져온 그룹이 Swagger URL에 서비스 그룹 형식으로 추가된다`() {
    // Given: SwaggerGroupFetcher가 그룹 목록을 반환
    // When: SwaggerConfig가 초기화됨
    // Then: swagger-config에 서비스/그룹 형식의 URL이 포함됨
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls[?(@.name=='staff-gpt/API')]").exists()
        .jsonPath("$.urls[?(@.name=='staff-gpt/Maintenance')]").exists()
}
```

### 10.4 동적 페칭 실패 시 정적 swaggerGroups 설정을 폴백으로 사용한다
```kotlin
@Test
fun `동적 페칭 실패 시 정적 swaggerGroups 설정을 폴백으로 사용한다`() {
    // Given: 서비스가 응답하지 않고 정적 설정이 있음
    // application.yml: swagger-groups: [API, Maintenance]

    // When: SwaggerConfig가 초기화됨
    // Then: 정적 설정 기반으로 URL이 생성됨
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls[?(@.name=='fallback-service/API')]").exists()
}
```

### 10.5 그룹이 없는 서비스는 기존처럼 서비스 단위로 URL이 생성된다
```kotlin
@Test
fun `그룹이 없는 서비스는 기존처럼 서비스 단위로 URL이 생성된다`() {
    // Given: 서비스가 빈 그룹 목록을 반환
    wireMock.stubFor(
        get(urlPathEqualTo("/v3/api-docs/swagger-config"))
            .willReturn(okJson("""{"urls": []}"""))
    )

    // When: SwaggerConfig가 초기화됨
    // Then: 서비스 단위 URL이 생성됨
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls[?(@.name=='simple-service')].url")
        .isEqualTo("/v3/api-docs/simple-service")
}
```

### 10.6 /v3/api-docs/{service}/{group} 요청이 백엔드의 /v3/api-docs/{group}으로 라우팅된다
```kotlin
@Test
fun `그룹별 api-docs 요청이 백엔드의 해당 그룹으로 라우팅된다`() {
    // Given: 백엔드 서비스가 그룹별 api-docs를 제공
    wireMock.stubFor(
        get(urlPathEqualTo("/v3/api-docs/Maintenance"))
            .willReturn(okJson("""{"openapi": "3.1.0", "info": {"title": "Maintenance API"}}"""))
    )

    // When: Gateway를 통해 그룹별 api-docs 요청
    // Then: 백엔드의 해당 그룹 문서가 반환됨
    webTestClient
        .get()
        .uri("/v3/api-docs/user-service/Maintenance")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.info.title").isEqualTo("Maintenance API")
}
```

### 10.7 Swagger UI swagger-config에서 서비스/그룹 형식의 URL 목록이 반환된다
```kotlin
@Test
fun `Swagger UI swagger-config에서 서비스 그룹 형식의 URL 목록이 반환된다`() {
    // When: swagger-config 요청
    // Then: 서비스/그룹 형식의 URL 목록이 반환됨
    webTestClient
        .get()
        .uri("/v3/api-docs/swagger-config")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.urls").isArray
        .jsonPath("$.urls[*].name").value<List<String>> { names ->
            assertThat(names).anyMatch { it.contains("/") }
        }
}
```

## 구현

### SwaggerGroupFetcher.kt
```kotlin
@Service
class SwaggerGroupFetcher(
    private val webClientBuilder: WebClient.Builder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class SwaggerConfigResponse(
        val urls: List<SwaggerUrlInfo> = emptyList(),
    )

    data class SwaggerUrlInfo(
        val url: String = "",
        val name: String = "",
    )

    fun fetchGroups(serviceUrl: String): List<String> {
        return try {
            val webClient = webClientBuilder.baseUrl(serviceUrl).build()

            val response = webClient
                .get()
                .uri("/v3/api-docs/swagger-config")
                .retrieve()
                .bodyToMono(SwaggerConfigResponse::class.java)
                .block(Duration.ofSeconds(5))

            response?.urls?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Failed to fetch swagger groups from $serviceUrl: ${e.message}")
            emptyList()
        }
    }
}
```

### GatewayProperties.kt (확장)
```kotlin
data class ServiceConfig(
    val name: String = "",
    val path: String = "",
    val url: String = "",
    val stripPrefix: Int = 1,
    val hasPublicPath: Boolean = false,
    val swaggerEnabled: Boolean = true,
    val swaggerGroups: List<String> = emptyList(),  // 정적 폴백용
)
```

### SwaggerConfig.kt (수정)
```kotlin
@Configuration
class SwaggerConfig(
    swaggerUiConfigProperties: SwaggerUiConfigProperties,
    gatewayProperties: GatewayProperties,
    swaggerGroupFetcher: SwaggerGroupFetcher,
) {
    init {
        val urls = mutableSetOf<AbstractSwaggerUiConfigProperties.SwaggerUrl>()

        gatewayProperties.services
            .filter { it.swaggerEnabled }
            .forEach { service ->
                val groups = swaggerGroupFetcher.fetchGroups(service.url)
                    .ifEmpty { service.swaggerGroups }

                if (groups.isEmpty()) {
                    urls.add(createSwaggerUrl(service.name, "${GatewayPaths.SWAGGER_DOCS}/${service.name}"))
                } else {
                    groups.forEach { groupName ->
                        urls.add(createSwaggerUrl(
                            "${service.name}/${groupName}",
                            "${GatewayPaths.SWAGGER_DOCS}/${service.name}/${groupName}"
                        ))
                    }
                }
            }

        swaggerUiConfigProperties.urls = urls
    }

    private fun createSwaggerUrl(name: String, url: String) =
        AbstractSwaggerUiConfigProperties.SwaggerUrl().apply {
            this.name = name
            this.url = url
        }
}
```

### GatewayConfig.kt (라우팅 추가)
```kotlin
// 그룹별 Swagger API docs 프록시 라우트 (더 구체적인 경로 먼저)
routes = routes.route("$SWAGGER_ROUTE_PREFIX${service.name}-group") { r ->
    r
        .path("${GatewayPaths.SWAGGER_DOCS}/${service.name}/{group}")
        .filters { f ->
            f.rewritePath(
                "${GatewayPaths.SWAGGER_DOCS}/${service.name}/(?<group>.*)",
                "${GatewayPaths.SWAGGER_DOCS}/\${group}"
            )
        }
        .uri(service.url)
}

// 기존 서비스 단위 라우트 유지
routes = routes.route("$SWAGGER_ROUTE_PREFIX${service.name}") { r ->
    r
        .path("${GatewayPaths.SWAGGER_DOCS}/${service.name}")
        .filters { f ->
            f.setPath(GatewayPaths.SWAGGER_DOCS)
        }
        .uri(service.url)
}
```

### application.yml 예시
```yaml
gateway:
  services:
    - name: staff-gpt
      path: /api/staff-gpt/**
      url: http://localhost:8081
      strip-prefix: 1
      has-public-path: true
      swagger-enabled: true
      swagger-groups:  # 폴백용 정적 설정
        - API
        - Maintenance
        - Batch
```

## Swagger UI 결과 예시

드롭다운에 다음과 같이 표시됩니다:

```
┌────────────────────────────┐
│ staff-gpt/API            ▼ │
├────────────────────────────┤
│ staff-gpt/API              │
│ staff-gpt/Maintenance      │
│ staff-gpt/Batch            │
│ staff-gpt/Gateway          │
│ staff-gpt/Open-API         │
│ staff-gpt/Internal-Dev     │
│ file-uploader              │
└────────────────────────────┘
```

## 완료 조건
- [ ] 10.1 SwaggerGroupFetcher가 서비스의 swagger-config에서 그룹 목록을 가져온다
- [ ] 10.2 서비스 연결 실패 시 빈 목록을 반환한다
- [ ] 10.3 동적으로 가져온 그룹이 Swagger URL에 서비스/그룹 형식으로 추가된다
- [ ] 10.4 동적 페칭 실패 시 정적 swaggerGroups 설정을 폴백으로 사용한다
- [ ] 10.5 그룹이 없는 서비스는 기존처럼 서비스 단위로 URL이 생성된다
- [ ] 10.6 /v3/api-docs/{service}/{group} 요청이 백엔드의 /v3/api-docs/{group}으로 라우팅된다
- [ ] 10.7 Swagger UI swagger-config에서 서비스/그룹 형식의 URL 목록이 반환된다
