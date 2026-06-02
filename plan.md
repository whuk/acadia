# Gateway Hardening Plan

게이트웨이 코드 리뷰에서 검증된 보안·성능·신뢰성 결함을 TDD 사이클(Red → Green → Refactor)로 수정한다.
각 항목은 독립적인 동작 변경이며, 별도 커밋으로 분리한다. 구조적 변경이 선행되어야 하는 경우 해당 단계에서 먼저 수행하고 별도 커밋한다.

> 진행 방식: "go" 시 아래에서 체크되지 않은 첫 테스트를 구현하고, 그 테스트만 통과시키는 최소 코드를 작성한다.
> 테스트는 `Thread.sleep` 없이 결정적으로 작성한다. 게이트웨이 통합 테스트는 `@SpringBootTest`(RANDOM_PORT) + WireMock 패턴을 따른다.

---

## 1. [P1] Swagger 부팅 중 RestClient 타임아웃 부재 (REL — 3a)

**문제:** `RestClientConfig`의 `RestClient.builder().build()`에 connect/read 타임아웃이 없다(`RestClientConfig.kt:10`).
`SwaggerConfig` init 블록이 부팅 중 동기 호출(`SwaggerGroupFetcher.fetchGroups`)을 수행하므로, 백엔드가 연결은 수락하되 응답하지 않으면 기동이 무기한 블로킹된다.
게이트웨이 프록시용 `spring.cloud.gateway...httpclient` 타임아웃은 이 RestClient에 적용되지 않는다.

- [x] shouldReturnEmptyGroupsWhenBackendUnreachable — 연결 거부 백엔드에 대해 빈 리스트 반환 (기존 `SwaggerGroupFetcherTest`가 이미 커버)
- [x] shouldConfigureBoundedTimeoutsOnRestClient — `RestClient` 빈에 connect(1s)/read(2s) 타임아웃 적용. 동작 검증: 응답 지연(5s) 백엔드에 대해 read 타임아웃으로 빈 목록 반환 (`RestClientConfigTest`). Spring Boot 4에서 `HttpClientSettings` + `ClientHttpRequestFactoryBuilder.detect()` 사용

---

## 2. [P1] JWT context-path 인증 바이패스 (SEC — 1a)

**문제:** `JwtAuthenticationFilter`가 `request.requestURI`(컨텍스트 패스 포함)로 `/api/` 매칭한다(`JwtAuthenticationFilter.kt:50,58,103`).
라우팅은 컨텍스트 상대 경로로 매칭되므로, `server.servlet.context-path` 설정 시 `shouldNotFilter`가 보호 경로를 면제로 오판하여 인증을 전체 스킵한다. 동일 결함이 public 경로 판정·신뢰 헤더 strip에도 전파된다.

- [x] shouldResolveGatewayRelativePathExcludingContextPath — `RequestPaths.gatewayRelative`가 `requestURI`에서 `contextPath`를 제거한 상대 경로를 반환 (`RequestPathsTest`). 필터의 `shouldNotFilter`/경로 판정이 이 헬퍼를 사용하도록 변경
- [x] shouldRequireAuthOnProtectedPathUnderContextPath — `context-path=/gateway` 하에서 보호 경로가 토큰 없이 401 (`JwtContextPathTest`). 원복 시 RED 확인 완료
- [~] shouldStripTrustedHeadersOnPublicPathUnderContextPath — **드롭**. context-path 하에서는 Spring Cloud Gateway MVC 라우팅이 컨텍스트 접두사를 처리하지 못해 모든 라우트가 404가 되어 백엔드 도달이 불가하므로 end-to-end 관찰 불가. 공개경로 strip 자체는 비-context 경로에서 기존 `JwtHeaderInjectionTest`(SEC-2)로 커버됨. 필터는 동일 헬퍼를 `isPublicPath`에도 적용하여 동작은 일관됨

---

## 3. [P2] RateLimit 매 요청 O(N) 전체 스캔 (PERF — 2a)

**문제:** `RateLimitFilter.kt:45`가 매 요청마다 `requestCounts.entries.removeIf { ... }`로 전체 맵을 선형 순회한다.
고유 IP 고카디널리티에서 요청당 O(N) → 누적 O(N²) 비용이 발생한다.

- [x] shouldEvictStaleEntriesViaSweep — `sweep()`가 만료 엔트리만 제거하고 활성 엔트리는 보존 (`RateLimitEvictionTest`). 주입형 `clock`으로 결정적 검증. `@Scheduled(fixedDelay=60s)` + `@EnableScheduling`로 주기 실행
- [x] shouldNotScanEntireMapOnEachRequest — 요청 경로에서 전역 `removeIf` 제거. sweep 없이 다른 IP 엔트리가 유지됨을 검증(2개). 원복 시 RED 확인
- [x] shouldStillEnforceLimitAfterEvictionChange — burst 초과 429 및 윈도우 경과 후 리셋 유지(단위), 기존 통합 `RateLimitTest`도 그린

---

## 4. [P2] RateLimit IP 오추출 — 프록시 하단 (PERF/보안 — 2b)

**문제:** `RateLimitFilter.kt:41`이 `request.remoteAddr`만 사용한다. L7 프록시/ALB 하단에서는 모든 사용자가 단일 프록시 IP 풀을 공유한다.

- [x] shouldUseForwardedClientIpWhenTrustedProxyConfigured — `trustForwardedFor=true`일 때 XFF 최좌측 IP로 버킷 식별 (`RateLimitClientIpTest`). 동일 XFF는 remoteAddr가 달라도 단일 버킷
- [x] shouldFallBackToRemoteAddrWhenNoForwardedHeader — XFF 없으면 remoteAddr 폴백(가드)
- [x] shouldIgnoreForwardedHeaderWhenProxyNotTrusted — 기본값 `trustForwardedFor=false`에서 XFF 무시, remoteAddr 사용(스푸핑 방지 가드). 최좌측 신뢰는 직속 업스트림이 신뢰 프록시라는 전제이며 주석에 명시

> 설계 주의: XFF는 클라이언트가 위조 가능하므로, "신뢰 가능한 프록시" 설정이 있을 때만 파싱한다. 무조건 신뢰 금지.

---

## 5. [P2] 백엔드 연결 유실 에러 매핑 누락 (SEC/REL — 1c)

**문제(가설):** 백엔드 연결 거부 시 `ResourceAccessException`이 전파되어 기본 500 + 정보 유출.
**조사 결과:** 가설 반증됨. 연결 거부는 Gateway MVC 프록시가 이미 **502**로 처리하며, 본문은 Spring 기본 에러 JSON으로 유출이 없다. 상태코드 갭·정보 유출 모두 미발생. 남은 차이는 본문이 RFC 9457 ProblemDetail이 아니라는 **포맷 비통일**뿐이며, 이는 프록시 응답 레벨에서 본문을 재작성해야 하는 침습적 변경이라 보류한다([[보류]] B3).

- [x] shouldReturn502WhenBackendConnectionRefused — **조사 결과 1c 재현 안 됨**: 연결 거부는 이미 502 반환(Gateway MVC 프록시가 처리), 본문은 Spring 기본 에러 JSON(`timestamp/status/error/path`)으로 **스택트레이스·백엔드 호스트·패키지 유출 없음**. 따라서 투기적 `@ExceptionHandler`(이 예외는 advice까지 전파되지 않아 미발화) 추가 대신 안전 동작을 잠그는 회귀 테스트 작성 (`BackendUnavailableTest`: 502 + no-leak). 운영 코드 변경 없음
- [x] shouldPreserveGatewayOriginated503And504 — `BackendErrorFilter`의 passthrough 경계 단위 테스트(500→502, 503/504 보존, `BackendErrorFilterTest`). 통합 레벨은 기존 `CircuitBreakerTest`(503)·`ConnectionTimeoutTest`/`ResponseTimeoutTest`(504)가 이미 커버

---

## 6. [P3] routes.reduce 빈 목록 부팅 크래시 (REL — 3b)

**문제:** `GatewayConfig.kt:92` `routes.reduce { ... }`는 `props.services`가 비면 `UnsupportedOperationException`을 던져 컨텍스트 기동이 실패한다.

- [x] shouldBuildRouterWithoutErrorWhenNoServicesConfigured — services가 비어도 `gatewayRoutes()`가 예외 없이 no-op `RouterFunction`을 반환 (`GatewayConfigTest`). `reduce` → `reduceOrNull` + `RouterFunction { Optional.empty() }` 폴백. 원복 시 `UnsupportedOperationException` RED 확인

---

## 보류 / 논의 필요 (자동 수정하지 않음)

아래 항목은 리뷰에서 지적되었으나 **이 프로젝트의 명문화된 규칙·설계 의도와 충돌**하므로, 규칙 재검토 합의 전에는 수정하지 않는다.

### B1. Trace/Span 헤더 강제 생성 (리뷰 1b)
- `TraceIdFilter`/`SpanIdFilter`가 인입 헤더 무시하고 항상 생성. 그러나 `gateway-observability.md §5`가 "X-B3-* 는 생성하여 전달"로 명시. SpanId hop별 생성은 B3 표준상 정상. → 결함 아님. 트레이스 연속성을 위해 traceId 보존이 필요하다면 **규칙(§5) 개정 후** 진행.

### B2. FileLogStorage 요청당 open/close (리뷰 2c)
- `FileLogStorage.kt:24-29`의 요청마다 `mkdirs()` + FileWriter open/append/close는 비효율(버퍼링 부재)이 맞다. 단 `storage: none`이 기본이라 평소 비활성이고, `gateway-observability.md §1`은 가상 스레드 위 블로킹 file I/O를 명시적으로 허용. 권장 방향(STDOUT + 외부 수집기)은 규칙과 일치하므로, 별도 관찰성 개선 작업으로 분리한다.

### B3. 프록시 에러 본문 ProblemDetail 통일 (리뷰 1c 잔여)
- 백엔드 연결 거부/5xx 변환 시 본문이 Spring 기본 에러 JSON이다(상태코드 502는 정상, 유출 없음). `gateway-filter.md §6`의 ProblemDetail 통일을 엄밀히 적용하려면 `BackendErrorFilter`가 status뿐 아니라 본문까지 재작성해야 한다. 침습도 대비 효용이 낮아 별도 작업으로 분리한다.

---

## 작업 규율 (CLAUDE.md 준수)

- 구조적 변경과 동작 변경을 같은 커밋에 섞지 않는다. 둘 다 필요하면 구조적 변경을 먼저 커밋한다.
- 커밋 메시지에 구조적/동작 변경 여부를 명시한다.
- 매 단계 전체 테스트(장기 실행 제외)를 실행하여 회귀를 확인한다.
- 사용자 요청 전까지 자동 커밋하지 않는다. 변경 결과를 먼저 보고한다.
