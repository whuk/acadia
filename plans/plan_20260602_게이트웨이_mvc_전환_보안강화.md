# API Gateway 전환 계획서 (WebFlux → MVC + 가상 스레드, TDD)

## 프로젝트 정보
- **프로젝트명**: Acadia
- **기본 패키지**: me.ryan.acadia
- **Java 버전**: 21 (가상 스레드)
- **Kotlin 버전**: 2.2
- **Spring Boot**: 4.0.0
- **Spring Cloud Gateway**: 2025.1.0 (server-webmvc)

## 개요
기존 WebFlux 기반 API Gateway를 **Spring Cloud Gateway MVC + Java 21 가상 스레드**로 전환하고,
종합 분석에서 도출된 보안/신뢰성/품질/인프라 이슈를 함께 수정한다.

- 기존 MVP plan은 `plans/api-gateway-mvp-webflux.md`에 아카이브됨.
- 기존 동작(63개 명세)은 behavior parity로 유지하고, 보안 수정은 신규 실패 테스트를 먼저 추가한다.
- 각 Phase는 green 후 구조/동작 변경을 분리 커밋(Tidy First).

---

## Phase A: 빌드·런타임 MVC 전환
- [x] A.1 build.gradle.kts가 gateway-server-webmvc starter를 사용한다 (webflux starter 제거, starter-web exclude 제거)
- [x] A.2 springdoc-openapi-starter-webmvc-ui로 교체된다
- [x] A.3 애플리케이션이 servlet(Tomcat) 스택으로 정상 기동된다
- [x] A.4 가상 스레드가 활성화된다 (spring.threads.virtual.enabled=true)
- [x] A.5 Actuator health endpoint가 200을 반환한다
- [x] A.6 ktlint가 통과한다

## Phase B: 라우팅 (RouterFunction)
- [x] B.1 /api/users/** 요청이 user-service로 라우팅된다
- [x] B.2 /api/orders/** 요청이 order-service로 라우팅된다
- [x] B.3 정의되지 않은 경로는 404를 반환한다
- [x] B.4 라우팅 시 원본 HTTP 메서드가 유지된다
- [x] B.5 라우팅 시 원본 헤더가 전달된다
- [x] B.6 라우팅 시 원본 바디가 전달된다
- [x] B.7 stripPrefix가 적용되어 백엔드 경로가 올바르게 전달된다

## Phase C: 횡단 필터 (RequestId / Trace / Span)
- [x] C.1 모든 요청에 X-Request-Id가 생성된다
- [x] C.2 클라이언트가 보낸 X-Request-Id가 있으면 유지된다
- [x] C.3 TraceId가 백엔드로 전달된다
- [x] C.4 SpanId가 백엔드로 전달된다

## Phase D: 인증 (JWT) + 보안 수정
- [x] D.1 Authorization 헤더 없는 요청은 401을 반환한다
- [x] D.2 잘못된 JWT 토큰은 401을 반환한다
- [x] D.3 만료된 JWT 토큰은 401을 반환한다 (SEC-1: 동기 응답으로 race 제거)
- [x] D.4 유효한 JWT 토큰은 라우팅이 진행된다
- [x] D.5 JWT에서 추출한 사용자 ID가 X-User-Id 헤더로 전달된다
- [x] D.6 JWT에서 추출한 역할이 X-User-Roles 헤더로 전달된다
- [x] D.7 공개 경로(/api/public/**)는 인증 없이 접근 가능하다
- [x] D.8 [SEC-2] roles 없는 토큰 + 클라이언트가 주입한 X-User-Roles는 다운스트림에 전달되지 않는다
- [x] D.9 [SEC-2] 클라이언트가 주입한 X-User-Id는 JWT subject로 덮어써진다
- [x] D.10 [SEC-4] /v3/api-docs로 시작하는 비인가 경계 경로(/v3/api-docs-evil)는 인증을 요구한다

## Phase E: 장애 대응 (Resilience)
- [x] E.1 백엔드 응답이 3초 초과 시 504를 반환한다
- [x] E.2 백엔드 연결이 1초 초과 시 504를 반환한다
- [x] E.3 백엔드 실패 시 최대 3회 재시도한다
- [x] E.4 실패율 50% 초과 시 Circuit Breaker가 열린다
- [x] E.5 Circuit Breaker 열린 상태에서 503을 반환한다
- [x] E.6 백엔드 5xx 오류는 502로 변환된다

## Phase F: Rate Limiting + REL-1
- [x] F.1 초당 제한 초과 시 429를 반환한다
- [x] F.2 버스트까지 허용된다
- [x] F.3 Rate Limit 헤더가 응답에 포함된다 (Limit/Remaining/Reset)
- [x] F.4 Rate Limiting이 비활성화되면 제한 없이 통과하고 헤더가 없다
- [x] F.5 [REL-1] 윈도우 만료된 IP 항목이 정리되어 메모리가 무한 증가하지 않는다
- [x] F.6 [REL-1] limit/burst 설정의 의미가 명확히 적용된다 (죽은 설정 제거)

## Phase G: 로깅 & 트레이싱 + 바디 로깅 + SEC-5/6
- [x] G.1 요청 로그가 JSON 형식으로 기록된다
- [x] G.2 응답 로그가 JSON 형식으로 기록된다
- [x] G.3 요청 바디가 JSON 형식으로 로그에 기록된다
- [x] G.4 응답 바디가 JSON 형식으로 로그에 기록된다
- [x] G.5 바디 로깅은 설정으로 활성화/비활성화할 수 있다
- [x] G.6 바디 크기가 최대 크기를 초과하면 잘라서 기록된다
- [x] G.7 민감한 필드(password, token 등)는 마스킹된다
- [x] G.8 multipart/form-data 요청은 바디 로깅에서 제외된다
- [x] G.9 [SEC-5] 에러/로그 응답이 문자열 보간이 아닌 JSON 직렬화로 생성된다
- [x] G.10 [SEC-6] 민감 헤더(Authorization/Cookie/Set-Cookie/X-API-Key/X-User-*)가 요청·응답 로그에서 마스킹된다

## Phase H: CORS + Swagger (RestClient)
- [x] H.1 허용된 Origin에서 CORS preflight 요청이 성공한다
- [x] H.2 허용되지 않은 Origin은 CORS 오류를 반환한다
- [x] H.3 허용된 HTTP 메서드만 CORS 응답에 포함된다
- [x] H.4 credentials가 허용된다
- [x] H.5 SwaggerGroupFetcher가 RestClient로 그룹 목록을 동기 조회한다 (.block() 제거)
- [x] H.6 /v3/api-docs/{service}가 백엔드로 프록시된다
- [x] H.7 /v3/api-docs/{service}/{group}이 백엔드의 /v3/api-docs/{group}으로 라우팅된다
- [x] H.8 swagger-enabled: false인 서비스는 목록에서 제외된다
- [x] H.9 [SEC-7] prod에서 CORS allowed-origins는 환경변수로 주입되며 example.com 기본값이 적용되지 않는다

## Phase I: 품질 (에러 통일 + 필터 순서)
- [x] I.1 [QUAL-3] JWT/타임아웃/백엔드 에러 응답이 통일된 JSON 포맷으로 반환된다
- [x] I.2 [QUAL-1] 필터 순서가 인증 → ID/Trace → bodyCache → 로깅으로 결정적으로 적용된다

## Phase J: 인프라 + SEC-3/8
- [x] J.1 [SEC-3] JWT secret 미설정 시 애플리케이션이 기동에 실패한다 (기본값 제거 + fail-fast)
- [x] J.2 [SEC-8] prod에서 actuator health show-details가 제한되고 prometheus가 외부 노출되지 않는다
- [x] J.3 [INF-1] Dockerfile이 non-root 유저로 실행되고 HEALTHCHECK를 포함하며 베이스 이미지가 고정된다
- [x] J.4 [INF-2] docker-compose 비밀이 운영 재사용 금지로 명시된다
- [x] J.5 [INF-3] prod 로깅 storage가 명시된다
- [x] J.6 [INF-4] base 로깅 레벨이 INFO이고 DEBUG는 dev 프로파일에 한정된다
- [x] J.7 ./gradlew clean build 전체가 통과한다 (ktlint + 전체 테스트)

---

## 진행 상태
- Phase: A ~ J
- 사용법: `/go`(다음 미완료 테스트 구현), `./gradlew test`(테스트), `./gradlew clean build`(전체 검증)
