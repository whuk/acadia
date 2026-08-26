# Acadia API Gateway

Spring Cloud Gateway 기반의 API Gateway 서비스입니다.

## 개요

Acadia는 마이크로서비스 아키텍처를 위한 API Gateway로, 다음 기능을 제공합니다:

- **라우팅**: 경로 기반 백엔드 서비스 라우팅
- **인증**: JWT 기반 인증 및 사용자 정보 전달
- **장애 대응**: Circuit Breaker, Retry, Timeout
- **Rate Limiting**: 요청 속도 제한
- **로깅**: 구조화된 JSON 로깅 (요청/응답 바디, 헤더)
- **CORS**: Cross-Origin Resource Sharing 설정
- **메트릭**: Prometheus 메트릭 노출
- **Swagger 통합**: 백엔드 서비스 API 문서 통합

## 기술 스택

- Java 21
- Kotlin 2.2
- Spring Boot 4.x
- Spring Cloud Gateway 2025.x
- Resilience4j (Circuit Breaker)
- Micrometer (Prometheus 메트릭)

## 빠른 시작

### 요구 사항

- JDK 21+
- Gradle 8.x

### 빌드

```bash
./gradlew build
```

### 실행

```bash
./gradlew bootRun
```

### 테스트

```bash
./gradlew test
```

## 설정

### 서비스 라우팅

`application.yml`에서 백엔드 서비스를 설정합니다:

```yaml
gateway:
  services:
    - name: user-service
      path: /api/users/**
      url: http://localhost:8081
      strip-prefix: 1
      has-public-path: true    # /api/public/** 인증 제외
      swagger-enabled: true    # Swagger UI에 표시
      docs-path: /v3/api-docs  # 다운스트림 OpenAPI 문서 경로 (기본값)
    - name: order-service
      path: /api/orders/**
      url: http://localhost:8082
      strip-prefix: 1
      has-public-path: false
    - name: fastapi-service       # 비-springdoc 백엔드 예시
      path: /api/v1/**
      url: http://localhost:8000
      strip-prefix: 1
      docs-path: /openapi.json    # FastAPI는 /openapi.json에 문서 서빙
```

**`docs-path`** (기본값 `/v3/api-docs`): Swagger 통합 시 게이트웨이가 백엔드 OpenAPI 문서를 가져오는 경로입니다. springdoc 백엔드는 기본값을 그대로 쓰고, FastAPI처럼 `/openapi.json`에 문서를 서빙하는 **비-springdoc 백엔드**는 `docs-path: /openapi.json`으로 설정합니다. 그러면 `/v3/api-docs/{service}` 요청이 해당 경로로 프록시되어 Swagger UI 드롭다운에 정상 통합됩니다.

### JWT 인증

JWT 시크릿 키를 환경 변수로 설정합니다:

```bash
export JWT_SECRET=your-256-bit-secret-key-here
```

또는 `application.yml`:

```yaml
gateway:
  jwt:
    secret: ${JWT_SECRET:your-default-secret-key}
```

**인증 흐름:**
1. 클라이언트가 `Authorization: Bearer <token>` 헤더로 요청
2. Gateway가 JWT 검증
3. 유효한 토큰의 경우 `X-User-Id`, `X-User-Roles` 헤더를 백엔드로 전달
4. `/api/public/**` 경로는 인증 없이 접근 가능

**테스트용 JWT 토큰 생성:**

Gateway는 JWT 검증만 수행하며, 토큰 발급 기능은 없습니다. 개발/테스트 시 `JwtTestSupport` 클래스를 사용하여 토큰을 생성할 수 있습니다.

```kotlin
// 기본 토큰 생성
val token = JwtTestSupport.generateToken()

// 사용자 ID와 역할 지정
val token = JwtTestSupport.generateToken(
    userId = "user-123",
    roles = listOf("ADMIN", "USER"),
    expirationMs = 86400000  // 24시간
)

// Authorization 헤더 값 생성
val authHeader = JwtTestSupport.authHeader(userId = "user-123", roles = listOf("USER"))
```

토큰 생성 테스트 실행:
```bash
# JwtTokenGeneratorTest의 @Disabled 제거 후 실행
./gradlew test --tests "JwtTokenGeneratorTest.개발용*"
```

> **참고**: 테스트용 기본 시크릿 키는 `default-secret-key-for-testing-purposes-only-32bytes`입니다.

### Rate Limiting

```yaml
gateway:
  rate-limit:
    enabled: true
    limit: 10        # 초당 요청 수
    burst: 20        # 버스트 허용량
    window-ms: 1000  # 윈도우 크기 (ms)
```

응답 헤더:
- `X-RateLimit-Remaining`: 남은 요청 수
- `X-RateLimit-Reset`: 리셋 시간 (Unix timestamp)

### CORS

```yaml
gateway:
  cors:
    allowed-origins:
      - "https://example.com"
    allowed-methods:
      - GET
      - POST
      - PUT
      - DELETE
    allowed-headers:
      - "*"
    allow-credentials: true
    max-age: 3600
```

### 로깅

```yaml
gateway:
  logging:
    enabled: true
    storage: none           # none | file | db
    include-headers: true   # 응답 헤더 로깅
    include-body: true      # 요청/응답 바디 로깅
    include-query-params: true
    max-body-size: 10000    # 바디 최대 크기 (초과 시 잘림)
    file:
      path: ./logs/gateway-requests.log
```

**민감 정보 마스킹:** `password`, `token`, `secret`, `credential`, `apikey` 등의 필드는 자동으로 `***MASKED***`로 처리됩니다.

### Circuit Breaker

서비스마다 독립된 서킷 브레이커 인스턴스(`cb-{service.name}`)가 아래 기본 설정으로 생성됩니다. 한 백엔드의 장애로 서킷이 열려도 다른 백엔드 호출은 영향을 받지 않으며, 같은 서비스의 인증/공개 라우트는 하나의 인스턴스를 공유합니다. 서비스를 추가해도 resilience4j 설정을 늘릴 필요가 없습니다.

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50     # 실패율 50% 초과 시 열림
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
```

특정 서비스만 다른 임계값이 필요하면 해당 인스턴스를 오버라이드합니다:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      cb-user-service:
        failure-rate-threshold: 30
```

### Retry

```yaml
gateway:
  retry:
    retries: 3
    statuses:
      - INTERNAL_SERVER_ERROR
    methods:
      - GET
```

### Timeout

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          httpclient:
            connect-timeout: 1000    # 연결 타임아웃 (ms)
            read-timeout: 3s         # 읽기(유휴) 타임아웃
```

- **connect-timeout**: 백엔드 연결 수립 제한 시간. 연결 실패(거부/타임아웃)는 `502`로 매핑됩니다.
- **read-timeout**: 읽기 간 **유휴(idle)** 타임아웃입니다. 데이터가 흐르는 동안에는 만료되지 않고, 백엔드가 응답을 멈추면(데이터 없음) `504`로 매핑됩니다. 이 유휴 의미 덕분에 SSE 같은 장시간 스트림이 유지됩니다(아래 *프록시 클라이언트* 참조).

### 프록시 클라이언트

게이트웨이는 다운스트림 호출에 **Apache HttpComponents(HTTP/1.1)** 클라이언트를 사용합니다(`GatewayHttpClientConfig`).

- **HTTP/1.1 고정**: JDK 기본 HttpClient(HTTP/2)는 cleartext 연결에서 h2c 업그레이드(`Upgrade: h2c`, `HTTP2-Settings`)를 시도합니다. uvicorn/h11 같은 **엄격한 HTTP/1.1 백엔드**는 바디 있는 요청(POST/PUT/PATCH)을 `400`으로 거부합니다. HttpComponents는 평문 HTTP/1.1로 통신하고 **응답 헤더 케이스를 보존**합니다.
- **스트리밍(SSE) 지원**: `read-timeout`이 읽기 간 유휴 타임아웃이라 `text/event-stream` 같은 장시간 스트림이 데이터가 계속 흐르는 한 취소되지 않습니다.

> 응답 바디 로깅(`include-body: true`)은 응답을 버퍼링하므로 SSE 실시간성이 깨집니다. 스트리밍 백엔드를 프록시할 때는 기본값(`false`)으로 두세요.

## API 엔드포인트

### Actuator

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /actuator/health` | 헬스 체크 |
| `GET /actuator/prometheus` | Prometheus 메트릭 |
| `GET /actuator/gateway/routes` | 라우트 목록 |

### Swagger UI

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /swagger-ui.html` | Swagger UI |
| `GET /v3/api-docs` | Gateway OpenAPI 스펙 |
| `GET /v3/api-docs/{service}` | 서비스별 API 문서 (백엔드의 `docs-path`로 프록시) |

## 응답 코드

| 코드 | 상황 |
|-----|------|
| `401 Unauthorized` | JWT 토큰 없음/유효하지 않음/만료됨 |
| `429 Too Many Requests` | Rate Limit 초과 |
| `502 Bad Gateway` | 백엔드 5xx 오류 또는 연결 실패(거부/connect 타임아웃) |
| `503 Service Unavailable` | Circuit Breaker 열림 |
| `504 Gateway Timeout` | 백엔드 응답(read) 타임아웃 |

## 요청 헤더

Gateway가 백엔드로 전달하는 헤더:

| 헤더 | 설명 |
|-----|------|
| `X-Request-Id` | 요청 추적 ID (없으면 자동 생성) |
| `X-User-Id` | JWT에서 추출한 사용자 ID |
| `X-User-Roles` | JWT에서 추출한 역할 목록 |
| `X-B3-TraceId` | 분산 추적 Trace ID |
| `X-B3-SpanId` | 분산 추적 Span ID |

## 프로젝트 구조

```
src/main/kotlin/me/ryan/acadia/
├── AcadiaApplication.kt          # 메인 애플리케이션
├── config/                       # 설정 클래스
│   ├── GatewayConfig.kt          # 라우팅 설정
│   ├── GatewayProperties.kt      # 서비스 프로퍼티 (docs-path 등)
│   ├── GatewayHttpClientConfig.kt # 프록시 클라이언트 (Apache HttpComponents, HTTP/1.1)
│   ├── JwtProperties.kt          # JWT 프로퍼티
│   ├── CorsConfig.kt             # CORS 설정
│   ├── RateLimitProperties.kt    # Rate Limit 프로퍼티
│   ├── LoggingProperties.kt      # 로깅 프로퍼티
│   └── SwaggerConfig.kt          # Swagger 설정
├── filter/                       # Gateway 필터
│   ├── JwtAuthenticationFilter.kt
│   ├── RateLimitFilter.kt
│   ├── RequestIdFilter.kt
│   ├── RequestLoggingFilter.kt
│   ├── ResponseLoggingFilter.kt
│   ├── TraceIdFilter.kt
│   ├── SpanIdFilter.kt
│   └── BackendErrorFilter.kt
├── logging/                      # 로깅 시스템
│   ├── LogStorage.kt
│   ├── ConsoleLogStorage.kt
│   ├── FileLogStorage.kt
│   ├── DatabaseLogStorage.kt
│   └── SensitiveFieldMasker.kt
└── swagger/                      # Swagger 통합
    └── SwaggerGroupFetcher.kt
```

## 문서

자세한 구현 문서는 `docs/` 디렉토리를 참조하세요:

- [Phase 1: 프로젝트 설정](docs/phase-1-setup.md)
- [Phase 2: 기본 라우팅](docs/phase-2-routing.md)
- [Phase 3: JWT 인증](docs/phase-3-authentication.md)
- [Phase 4: 장애 대응](docs/phase-4-resilience.md)
- [Phase 5: 로깅 & 트레이싱](docs/phase-5-observability.md)
- [Phase 6: CORS & 보안](docs/phase-6-cors-security.md)
- [Phase 7: Prometheus 메트릭](docs/phase-7-metrics.md)
- [Phase 8: Rate Limiting](docs/phase-8-rate-limiting.md)
- [Phase 9: Swagger Aggregation](docs/phase-9-swagger-aggregation.md)
- [Phase 10: Swagger 그룹 통합](docs/phase-10-swagger-group-integration.md)
- [Phase 11: 요청/응답 바디 로깅](docs/phase-11-body-logging.md)

## 라이선스

MIT License
