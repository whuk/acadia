# 에러 응답 ProblemDetail 통일 Plan (B3)

게이트웨이 에러 본문 형식을 RFC 9457 ProblemDetail로 통일한다.
프록시(연결 거부/5xx)·미정의 경로(404) 등은 servlet 에러 디스패치(`/error`)로 흘러 Spring 기본 에러 JSON
(`timestamp/status/error/path`)을 반환한다. 이를 커스텀 `ErrorController`로 ProblemDetail(`type/title/status/instance`,
`application/problem+json`)로 바꾼다. **프록시 응답 스트리밍 경로는 건드리지 않는다.**

> 배경: `GatewayExceptionHandler`(circuit open 503 / CB status 502)는 이미 ProblemDetail. 프록시 직접-write 502/404 등은
> 예외가 advice까지 전파되지 않아 `/error`로 빠진다. `ErrorController`를 제공하면 BasicErrorController가
> `@ConditionalOnMissingBean(ErrorController)`로 비활성화된다.

## 1. 에러 본문 통일

- [x] shouldReturnProblemDetailOnBackendUnavailable — 백엔드 연결 거부 502가 `application/problem+json` ProblemDetail(title/status:502) 본문, 유출 없음 (`GatewayErrorFormatTest`). 컨트롤러 없을 때 `application/json` RED 확인
- [x] shouldMapErrorAttributesToProblemDetail — `GatewayErrorController`가 `ERROR_STATUS_CODE`/`ERROR_REQUEST_URI`를 status·instance가 채워진 `ResponseEntity<ProblemDetail>`(problem+json)로 매핑, 속성 없으면 500 (`GatewayErrorControllerTest`)

## 비고

- 401(JwtAuthenticationFilter가 직접 write)은 `/error`를 거치지 않으므로 이번 범위 밖. 필요 시 별도 통일 작업.
- 기존 `UndefinedRouteTest`(404 상태만 검증), `BackendUnavailableTest`(502 + no-leak)는 영향 없이 통과해야 한다.

## 작업 규율 (CLAUDE.md)
- API 수준 실패 테스트 먼저 → 구현 → 단위 테스트. 전체 테스트·ktlint 통과 확인. 사용자 요청 전 자동 커밋 금지.
