# 규칙 적용 범위 (rules-readme)

이 저장소(`acadia`)는 **Spring Cloud Gateway Server WebMVC + Java 21 가상 스레드** 기반 API 게이트웨이다. `rules/`의 규칙은 적용 대상이 다르므로 혼동하지 않는다.

## 게이트웨이 코드에 적용하는 규칙

현재 `src/main`의 게이트웨이 코드(라우팅, 필터, 로깅)에 적용한다.

| 규칙 | 범위 |
|---|---|
| `gateway-filter.md` | servlet 필터 모델, 필터 순서(`FilterOrders`), 라우팅(RouterFunction), Resilience, 에러 통일, 블로킹 정책 |
| `gateway-security.md` | 인증 적용 범위, 신뢰 헤더 strip-then-set, 경로 경계, JWT 시크릿, CORS |
| `gateway-observability.md` | 로그 저장소(console/file), 민감정보 마스킹, 바디 캐싱, 로깅 타이밍, 트레이싱 |

## 백엔드 서비스 모듈용 규칙 (현재 게이트웨이 코드에는 미적용)

아래 규칙들은 JPA + DDD Rich Domain + OpenAPI 코드생성 + Finder/Service 분리 기반의 **백엔드 서비스 모듈**을 가정한다. 게이트웨이는 도메인/서비스/컨트롤러/영속성 계층이 없으므로 적용 대상이 없다. 향후 백엔드 서비스 모듈을 추가할 때 적용한다.

- `domain.md`, `repository.md`, `service-layer.md`, `api-dto.md`, `rest-api.md`, `layer-communication-rules.md`

## 공통

- `test.md`의 "필요한 최소 컨텍스트만 로드", "결정적 테스트(Thread.sleep 금지)" 원칙은 게이트웨이 테스트에도 적용한다. 단 게이트웨이는 MongoDB/JPA base class를 사용하지 않으며, `@SpringBootTest` + `WebTestClient.bindToServer`(servlet RANDOM_PORT) + WireMock 패턴을 사용한다.
